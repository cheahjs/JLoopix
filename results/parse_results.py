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
import matplotlib.pyplot as plt
import matplotlib
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


def mean_variance(rate, data):
    totals = [x[5] for x in data]
    reals = [x[6] for x in data]
    total = np.mean(totals)
    total_std = np.std(totals)
    real = np.mean(reals)
    real_std = np.std(reals)
    return (data[0], total, total_std, real, real_std)


pool = Pool()

# Plot bandwidth data
bw_folders = glob.glob('bandwidth/*')
bandwidth_data = pool.map(lambda x: get_data_for_file(
    *x), zip(bw_folders, itertools.repeat([31001, 31450, 32001])))
flat_data = sorted([item for x in bandwidth_data for item in x],
                   lambda x, y: cmp(x[4], y[4]))

java_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] == 31001], lambda x: x[4])]
python_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] == 31450], lambda x: x[4])]
mix_data = [mean_variance(rate, list(items)) for rate, items in itertools.groupby(
    [x for x in flat_data if x[0] == 32001], lambda x: x[4])]

lines = {'rate': [], 'expected': [], 'expected real': [],
         'total': [], 'total_err': [], 'real': [], 'real_err': []}
for data in java_data:
    lines['rate'].append(data[0][4])
    lines['expected'].append(data[0][4])
    lines['expected real'].append(data[0][3])
    lines['total'].append(data[1])
    lines['total_err'].append(data[2])
    lines['real'].append(data[3])
    lines['real_err'].append(data[4])

matplotlib.rcParams['figure.figsize'] = (15, 7)

plt.plot('rate', 'expected', data=lines, marker='', linewidth=1,
         linestyle='--', label='All traffic (Expected)')
plt.plot('rate', 'expected real', data=lines, marker='', linewidth=1,
         linestyle='--', label='Real traffic (Expected)')
plt.errorbar(lines['rate'], lines['total'], yerr=lines['total_err'], marker='x',
             linewidth=1, label='All traffic', capsize=5)
plt.errorbar(lines['rate'], lines['real'], yerr=lines['real_err'], marker='x',
             linewidth=1, label='Real traffic', capsize=5)

lines['total'], lines['total_err'], lines['real'], lines['real_err'] = [], [], [], []
for data in python_data:
    lines['total'].append(data[1])
    lines['total_err'].append(data[2])
    lines['real'].append(data[3])
    lines['real_err'].append(data[4])

plt.xlabel('Rate of sending messages ($\lambda$) per second')
plt.ylabel('Messages sent per second')
plt.xlim(0, 9)
plt.ylim(0, 9)
plt.grid()
plt.legend()
plt.title('Traffic sent by client')
plt.savefig('client_bandwidth.pdf', bbox_inches='tight')
plt.close()
