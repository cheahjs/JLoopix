# JLoopix
[![Build Status](https://travis-ci.org/cheahjs/JLoopix.svg?branch=master)](https://travis-ci.org/cheahjs/JLoopix)

A Java library that implements the [Loopix anonymous communication system](https://arxiv.org/abs/1703.00536).

Based off the original [Python implementation](https://github.com/UCL-InfoSec/loopix).

## Instructions

Tested on:

* Ubuntu 16.04
* Ubuntu 14.04/16.04 for Windows.
    * Docker running on Windows, scripts run from Bash.

**By default, the test network will consume ~100GB of RAM! Modify `tools/run_all_gather_data.sh` to reduce the number of clients.**

### Install dependencies

```bash
apt install openjdk-8-jdk openjfx docker.io python2.7 python-pip python-dev build-essential libssl-dev libffi-dev python-tk libpcap-dev tshark
pip install numpy scipy sphinxmix==0.0.6 petlib twisted matplotlib scapy multiprocessing dpkt
```

Known issues:

* sphinxmix 0.0.7 is incompatible with sphinxmix 0.0.6. `too many values to unpack` errors are usually the result of the wrong version of sphinxmix. 

### Setup

```bash
git clone https://github.com/cheahjs/JLoopix
cd JLoopix
git submodule init && git submodule update
pip install -e external/loopix
cd external/energybox
patch -p0 < ../energybox.patch
./gradlew jar
cd ../../tools
./build_jar.sh
./docker_build.sh
docker network create loopix_net
```

### Generate graphs

```bash
./run_all_gather_data.sh
./energybox.sh
cd ../results
python parse_results.py
```

### Run chat clients

```bash
./run_chat.sh
docker attach client_1
docker attach client_2
```

Known issues:

* Console cursor handling isn't the best. If the cursor is at the bottom of the viewport, the last line will be continuously overriden.

## Tools

* `tools/build_jar.sh`: Runs gradle to build a JLoopix standalone JAR.
* `tools/docker_build.sh`: Builds the JLoopix and Loopix Docker containers.
* `tools/docker_run_chat.sh`: Runs a test network, with the JLoopix clients running the chat demo.
* `tools/docker_run_latency.sh`: Runs a test network, with the JLoopix clients running the latency measurement tool.
* `tools/docker_run.sh`: Runs a test network.
* `tools/docker_stop.sh`: Stops all running JLoopix/Loopix containers.
* `tools/energybox.sh`: Filters packet captures to include only `client_1`, and runs [EnergyBox](https://github.com/rtslab/EnergyBox) on the filtered captures.
* `tools/gather_bandwidth_data.sh`: Runs tcpdump inside the Docker network.
* `tools/gather_latency_data.sh`: Copies the `latency.csv` file from inside JLoopix containers.
* `tools/gather_total_latency_data.sh`: Copies the `latency.csv` file from inside JLoopix containers.
* `tools/generate_keys.sh`: Generates Loopix keys and DB.
* `tools/loopix_db.py`: Find all keys in `build/loopix_keys`, and adds them to a SQLite DB.
* `tools/network_config.sh`: Contains the network topology used by other scripts.
* `tools/run_all_gather_data.sh`: Runs various test networks with different parameters to generate bandwidth and latency data.
* `tools/run_chat.sh`: Runs a 2 client network for chat.
* `tools/setup_network.py`: Generates various config files.
* `results/parse_results.py`: Generates graphs.