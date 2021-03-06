#!/usr/bin/python
# -*- coding: utf-8 -*-
import socket
import dpkt
import numpy as np
import math
import os
import petlib.pack
import glob
import fnmatch
import json
import itertools
from loopix.core import SphinxPacker
from sphinxmix import SphinxException
from sphinxmix.SphinxParams import SphinxParams
from sphinxmix.SphinxClient import Relay_flag, Dest_flag
from binascii import hexlify
import msgpack
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from multiprocessing import Pool

def readPacked(file_name):
    """Read msgpack encoded file"""
    data = file(file_name, "rb").read()
    return petlib.pack.decode(data)


def get_addr(host, port):
    """Returns port"""
    return "%d" % (port)


def read_keys(path):
    """Reads all the private keys and client-provider mappings from path"""
    keys = {}
    providers = {}
    nodes = {}
    for root, dirnames, filenames in os.walk(path):
        # Find all .prv files and extract the private keys
        for filename in fnmatch.filter(filenames, '*.prv'):
            node_name = os.path.basename(root)
            if "mixnode" in node_name:
                node_name = node_name.replace('mixnode', 'mix')
            full_path = os.path.join(root, filename)
            keys[node_name] = readPacked(full_path)

    for root, dirnames, filenames in os.walk(path):
        # Find all publicClient.bin files and extract provider info
        for filename in fnmatch.filter(filenames, 'publicClient.bin'):
            node_name = os.path.basename(root)
            full_path = os.path.join(root, filename)
            _, name, port, host, pub, prvinfo = readPacked(full_path)
            providers[get_addr(host, port)] = prvinfo
            nodes[get_addr(host, port)] = name

    for root, dirnames, filenames in os.walk(path):
        # Find all publicMixnode.bin files and extract provider info
        for filename in fnmatch.filter(filenames, 'publicMixnode.bin'):
            node_name = os.path.basename(root)
            full_path = os.path.join(root, filename)
            _, name, port, host, group, _ = readPacked(full_path)
            nodes[get_addr(host, port)] = name

    for root, dirnames, filenames in os.walk(path):
        # Find all publicMixnode.bin files and extract provider info
        for filename in fnmatch.filter(filenames, 'publicProvider.bin'):
            node_name = os.path.basename(root)
            full_path = os.path.join(root, filename)
            _, name, port, host, _ = readPacked(full_path)
            nodes[get_addr(host, port)] = name
    return (keys, providers, nodes)


packer = SphinxPacker((SphinxParams(header_len=1024), None))


def decrypt_packet(packet, initial_key, keys):
    """Decrypts packet with initial_key, and continues decrypting with keys"""
    data = packet.data.data.data
    key = initial_key
    decoded_packet = petlib.pack.decode(data)
    # Some of the packets sent are unencrypted SUBSCRIBE and PULL messages
    if decoded_packet[0] in ['SUBSCRIBE', 'PULL']:
        return decoded_packet[0], None
    routing_flag = Relay_flag
    while routing_flag != Dest_flag:
        try:
            # Decrypt sphinx packets
            tag, routing, new_header, new_body = packer.decrypt_sphinx_packet(
                decoded_packet, key)
        # Broken packets due to race conditions in Java client
        except SphinxException:
            print 'BROKEN %f' % packet.time
            return 'BROKEN', None
        except msgpack.exceptions.ExtraData:
            print 'BROKEN_2 %f' % packet.time
            return 'BROKEN_2', None
        except Exception as e:
            print 'BROKEN_3 %f' % packet.time
            print e
            return 'BROKEN_3', None
        routing_flag, meta_info = routing[0], routing[1:]
        decoded_packet = (new_header, new_body)
        if routing_flag == Relay_flag:
            # Continue decrypting if not a destination packet
            next_addr, drop_flag, type_flag, delay, next_name = meta_info[0]
            if drop_flag:
                return 'DROP', None
            key = keys[next_name]

    try:
        # Decode packet
        dest, msg = packer.handle_received_forward(new_body)
    # Broken packets due to race conditions in Java client
    except SphinxException:
        print 'BROKEN_BODY %f' % packet.time
        return 'BROKEN_BODY', None
    except msgpack.exceptions.ExtraData:
        print 'BROKEN_BODY_2 %f' % packet.time
        return 'BROKEN_BODY_2', None
    except Exception:
        print 'BROKEN_BODY_3 %f' % packet.time
        return 'BROKEN_BODY_3', None

    if msg.startswith('HT'):
        return 'LOOP', None
    return 'REAL', msg


def analyse_packets(packets, start_time, end_time):
    """Provides average count/time for packets"""
    print "Packet count: %d" % len(packets)
    total_time = end_time - start_time
    print "Total time: %f" % (total_time)
    # Calculate mean using packet count/time
    mean_count = len(packets) / (total_time)
    print "Mean (count/time): %f packets/s" % mean_count

    print "-----------------"

    # Calculate mean by splitting packets into second long intervals
    start_time, end_time = long(math.floor(
        start_time)), long(math.ceil(end_time))
    buckets = [[] for i in range(end_time-start_time)]
    for i in packets:
        bucket_index = long(math.floor(i.time))-start_time
        buckets[bucket_index].append(i)
    counts = [len(x) for x in buckets]
    mean_bucket = np.mean(counts)
    variance_bucket = np.var(counts)
    print "Mean (buckets): %f packets/s" % mean_bucket
    print "Variance (buckets): %f" % variance_bucket
    print "Std. dev. (buckets): %f" % math.sqrt(variance_bucket)

    print "-----------------"

    # Calculate mean by using the average packet interval time
    intervals = [packets[i].time - packets[i -
                                           1].time for i in range(1, len(packets))]
    mean_interval = 1/np.mean(intervals)
    print "Mean (intervals): %f packets/s" % mean_interval
    variance_interval = 1/np.var(intervals)
    print "Variance (intervals): %f" % variance_interval
    print "Std. dev. (intervals): %f" % (math.sqrt(variance_interval))

    return mean_count

def get_data_for_file(folder, ports):
    """Parses the pcap files in the specified folder, and outputs data for the specified ports
    """
    # Load private keys and port->provider mappings
    keys, providers, nodes = read_keys(os.path.join(folder, 'keys'))
    print 'Loading packets'
    # Load packets
    with open(os.path.join(folder, 'network.pcap'), 'rb') as f:
        cap = dpkt.pcap.Reader(f)
        packets = []
        for ts, buf in cap:
            eth = dpkt.sll.SLL(buf)
            if eth.type != 3:
                # tcpdump captures both type 3 and 4 packets, resulting in duplicates
                continue
            eth.time = ts
            try:
                eth.data.src = socket.inet_ntoa(eth.data.src)
                eth.data.dst = socket.inet_ntoa(eth.data.dst)
            except:
                pass
            packets.append(eth)
    # Load config
    config = json.load(open(os.path.join(folder, 'config.json')))
    # Invert exponential parameters to get rate
    loops, drop, payload = 1/config['EXP_PARAMS_LOOPS'], 1 / \
        config['EXP_PARAMS_DROP'], 1/config['EXP_PARAMS_PAYLOAD']
    lambda_total = loops + drop + payload
    print "λ_loop = %f, λ_drop = %f, λ_payload = %f, λ = %f" % (
        loops, drop, payload, lambda_total)

    data = []

    for port in ports:
        print "Parsing port %d from %s" % (port, folder)
        # Filter packets by source port
        filtered = [x for x in packets if x.data.data.sport == port]
        print "Analysing all packets"
        all_mean = analyse_packets(filtered, packets[0].time, packets[-1].time)

        print "-----------------"

        decrypted_filtered = [(x, decrypt_packet(
            x, keys[nodes[get_addr(x.data.dst, x.data.data.dport)]], keys)) for x in filtered]
        real_filtered = [
            x for x, decrypt in decrypted_filtered if decrypt[0] == 'REAL']
        if len(real_filtered) == 0:
            print "Warning, 0 real packets"
            real_mean = None
        else:
            print "Analysing real packets"
            real_mean = analyse_packets(
                real_filtered, packets[0].time, packets[-1].time)

        print "\n-----------------\n"

        data.append((port, loops, drop, payload,
                     lambda_total, all_mean, real_mean))

    return data


def get_data_for_file_mp(a):
    return get_data_for_file(*a)


def mean_variance(rate, data):
    totals = [x[5] for x in data]
    reals = [0 if x[6] is None else x[6] for x in data]
    total = np.mean(totals)
    total_std = np.std(totals)
    real = np.mean(reals)
    real_std = np.std(reals)
    return (data[0], total, total_std, real, real_std)


matplotlib.rcParams['figure.figsize'] = (10, 5)
pool = Pool()

# Plot bandwidth data
if os.path.exists('saved_bw_data.json'):
    with open('saved_bw_data.json', 'r') as f:
        bandwidth_data = json.load(f)
else:
    bw_folders = glob.glob('bandwidth/*')
    bandwidth_data = pool.map(get_data_for_file_mp, zip(
        bw_folders, itertools.repeat([31001, 31002, 31003,
        31151, 31152, 31153,
        32001, 32002, 32003])))

with open('saved_bw_data.json', 'w') as f:
    json.dump(bandwidth_data, f)

flat_data = sorted([item for x in bandwidth_data for item in x],
                   lambda x, y: cmp(x[4], y[4]))
java_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] in [31001, 31002, 31003]], lambda x: x[4])]
python_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] in [31151, 31152, 31153]], lambda x: x[4])]
mix_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] in [32001, 32002, 32003]], lambda x: x[4])]

lines = {'rate': [], 'expected': [], 'expected real': [],
         'total': [], 'total2': [], 'real': [], 'real2': [],
         'total_err': [], 'total2_err': [], 'real_err': [], 'real2_err': []}
for data in zip(java_data, python_data):
    lines['rate'].append(data[0][0][4])
    lines['expected'].append(data[0][0][4])
    lines['expected real'].append(data[0][0][3])
    lines['total'].append(data[0][1])
    lines['total2'].append(data[1][1])
    lines['total_err'].append(data[0][2])
    lines['total2_err'].append(data[1][2])
    lines['real'].append(data[0][3])
    lines['real2'].append(data[1][3])
    lines['real_err'].append(data[0][4])
    lines['real2_err'].append(data[1][4])

plt.plot('rate', 'expected', data=lines, marker='', linewidth=1,
         linestyle='--', label='All traffic (Expected)')
plt.plot('rate', 'expected real', data=lines, marker='', linewidth=1,
         linestyle='--', label='Real traffic (Expected)')
plt.errorbar(lines['rate'], lines['total'], yerr=lines['total_err'], marker='x',
         linewidth=1, label='All traffic', capsize=5)
plt.errorbar(lines['rate'], lines['total2'], yerr=lines['total2_err'], marker='o',
         linewidth=1, label='All traffic (Python)', capsize=5)
plt.errorbar(lines['rate'], lines['real'], yerr=lines['real_err'], marker='x',
         linewidth=1, label='Real traffic', capsize=5)
plt.errorbar(lines['rate'], lines['real2'], yerr=lines['real2_err'], marker='o',
         linewidth=1, label='Real traffic (Python)', capsize=5)
plt.xlabel('Rate of sending messages ($\lambda$) per second')
plt.ylabel('Messages sent per second')
plt.xlim(xmin=0)
plt.ylim(ymin=0)
plt.grid()
plt.legend()
plt.savefig('client_bandwidth.pdf', bbox_inches='tight')
plt.close()

plt.errorbar([x[0][4] for x in mix_data], [x[1] for x in mix_data], yerr=[x[2] for x in mix_data], marker='x',
             linewidth=1, label='All traffic', capsize=5)
plt.errorbar([x[0][4] for x in mix_data], [x[3] for x in mix_data], yerr=[x[4] for x in mix_data], marker='x',
             linewidth=1, label='Real traffic', capsize=5)
plt.xlabel('Rate of sending messages ($\lambda$) per second')
plt.ylabel('Messages sent per second')
plt.xlim(xmin=0)
plt.ylim(ymin=0)
plt.grid()
plt.legend()
plt.savefig('mix_bandwidth.pdf', bbox_inches='tight')
plt.close()

# Plot latency data
def get_latency_data_for_folder(folder):
    java = (glob.glob(os.path.join(folder, 'latency_?.csv')) + ['latency.csv'])[0]
    latency_data = [map(lambda y: long(y) / 1000000.0, x.split(',')) for x in open(
        java, 'r').readlines()]
    # take only the last 190s for steady state data
    end_ts = latency_data[-1][1]
    start_ts = end_ts - (190.0 * 1000)
    latency_data = [x for x in latency_data if x[1] >= start_ts]
    time_taken = [x[-1] for x in latency_data]
    mean = np.mean(time_taken)
    std = np.std(time_taken)
    java_data = (mean, std)

    python = glob.glob(os.path.join(folder, 'latency_python_*.csv'))[0]
    latency_data=[map(lambda y: float(y) * 1000, x.split(',')) for x in open(
        python, 'r').readlines()]
    # take only the last 190s for steady state data
    end_ts = latency_data[-1][1]
    start_ts = end_ts - (190.0 * 1000)
    latency_data = [x for x in latency_data if x[1] >= start_ts]
    time_taken = [x[-1] for x in latency_data]
    mean = np.mean(time_taken)
    std = np.std(time_taken)

    total_client = len(glob.glob(os.path.join(folder, 'logs', 'client_*')))
    return (total_client, java_data, (mean, std))


lat_folders = glob.glob('latency/*')
latency_data = sorted([get_latency_data_for_folder(folder) for folder in lat_folders], 
    lambda x, y: cmp(x[0], y[0]))
lat_clients = [x[0] for x in latency_data]
lat_avg = [x[1][0] for x in latency_data]
lat_err = [x[1][1] for x in latency_data]
lat_python_avg = [x[2][0] for x in latency_data]
lat_python_err = [x[2][1] for x in latency_data]
print latency_data
plt.errorbar(lat_clients, lat_avg, yerr=lat_err, label='Java client',
             marker='x', linewidth=1, capsize=5)
plt.errorbar(lat_clients, lat_python_avg, yerr=lat_python_err, label='Python client',
             marker='x', linewidth=1, capsize=5)
plt.xlabel('Number of clients')
plt.ylabel('Latency Overhead (ms)')
plt.grid()
plt.legend()
plt.savefig('client_latency.pdf', bbox_inches='tight')
plt.close()

# Plot total latency data
def get_total_latency_data_for_folder(folder):
    latency_data = [map(lambda y: long(y) / 1000000000.0, x.split(',')) for x in open(
        os.path.join(folder, 'latency.csv'), 'r').readlines()]
    # take only the last 190s for steady state data
    end_ts = latency_data[-1][1]
    start_ts = end_ts - 190.0
    latency_data = [x for x in latency_data if x[1] >= start_ts]
    base_ts = latency_data[0][1]
    latency_data = [(x[0]-base_ts, x[1]-base_ts, x[2]) for x in latency_data]
    latency_data = sorted(latency_data, lambda x, y: cmp(x[1], y[1]))
    ret = []
    prev = 0
    for i in latency_data:
        if i[0] < prev:
             continue
        prev = i[0]
        ret.append(i)
    return ret


lat_folders = glob.glob('latency_total/*')
all_latency_data = [get_total_latency_data_for_folder(
    folder) for folder in lat_folders]
latency_data = all_latency_data[0]
#print latency_data
fig, ax1 = plt.subplots()
color = 'tab:red'
ax1.set_xlabel('Message sent time (s)')
ax1.set_ylabel('Message received time (s)', color=color)
ax1.plot([x[1] for x in latency_data], [x[0] for x in latency_data], linewidth=1, color=color)
ax1.tick_params(axis='y', labelcolor=color)
ax1.set_ylim(0)
ax1.set_xlim(0)

color = 'tab:blue'
ax2 = ax1.twinx()
ax2.set_ylabel('Time taken (s)', color=color)
ax2.plot([x[1] for x in latency_data], [x[2] for x in latency_data], linewidth=1, color=color)
ax2.tick_params(axis='y', labelcolor=color)
ax2.set_ylim(0)
plt.grid()
ax1.grid(axis='x')
fig.tight_layout()
plt.savefig('client_total_latency.pdf', bbox_inches='tight')
plt.close()

# Plot energy consumption
bw_folders = glob.glob('bandwidth/*')

def get_energy_for_folder(folder):
    energy_files = glob.glob(os.path.join(folder, 'energy_*.txt'))
    energies = [float(open(file, 'r').readline()) for file in energy_files]
    energy = np.mean(energies)
    energy_std = np.std(energies)
    config = json.load(open(os.path.join(folder, 'config.json')))
    TIME = 180 # a lie, but close enough
    DAY = 60*60*24
    multiplier = DAY/TIME
    energy = energy*multiplier
    energy_std = energy_std*multiplier
    loops, drop, payload = 1 / config['EXP_PARAMS_LOOPS'], 1 / \
        config['EXP_PARAMS_DROP'], 1 / config['EXP_PARAMS_PAYLOAD']
    lambda_total = loops + drop + payload
    return (lambda_total, energy, energy_std)

energy_data = sorted(map(get_energy_for_folder, bw_folders), lambda x, y: cmp(x[0], y[0]))
print energy_data
plt.errorbar([x[0] for x in energy_data], [x[1] for x in energy_data], yerr=[x[2] for x in energy_data],
             marker='x', linewidth=1, capsize=5)
plt.xlabel('Rate of sending messages ($\lambda$) per second')
plt.ylabel('Daily network energy consumption (J)')
plt.xlim(xmin=0)
plt.grid()
plt.savefig('energy_use.pdf', bbox_inches='tight')
plt.close()
