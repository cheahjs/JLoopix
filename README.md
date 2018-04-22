# JLoopix
[![Build Status](https://travis-ci.org/cheahjs/JLoopix.svg?branch=master)](https://travis-ci.org/cheahjs/JLoopix)

A Java library that implements the [Loopix anonymous communication system](https://arxiv.org/abs/1703.00536).

Based off the original [Python implementation](https://github.com/UCL-InfoSec/loopix).

## Running

Tested on:

* Ubuntu 16.04
* Ubuntu 14.04/16.04 for Windows.
    * Docker running on Windows, scripts run from Bash.

**By default, the test network will consume ~100GB of RAM! Modify `tools/run_all_gather_data.sh` to reduce the number of clients.**

## Install dependencies

```bash
apt install openjdk-8-jdk openjfx docker.io python2.7 python-pip python-dev build-essential libssl-dev libffi-dev python-tk libpcap-dev tshark
pip install numpy scipy sphinxmix==0.0.6 petlib twisted matplotlib scapy multiprocessing dpkt
```

Known issues:

* sphinxmix 0.0.7 is incompatible with sphinxmix 0.0.6. `too many values to unpack` errors are usually the result of the wrong version of sphinxmix. 

## Setup

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

## Generate graphs

```bash
./run_all_gather_data.sh
./energybox.sh
cd ../results
python parse_results.py
```

## Run chat clients

```bash
./run_chat.sh
docker attach client_1
docker attach client_2
```

Known issues:

* Console cursor handling isn't the best. If the cursor is at the bottom of the viewport, the last line will be continously overriden.


<!-- ## Building

1. Clone the repo and submodules `git clone --recursive https://github.com/cheahjs/JLoopix`
2. `./build_jar.sh` to build JLoopix's JAR file located under `jloopix/build/libs/jloopix-standalone.jar`
3. `./generate_keys.sh` to generate pub/priv keys and database.
4. `./docker_build.sh` to build the Docker image.

## Running

1. `./docker_run.sh` to run a network with 1 provider, 6 mix nodes (2 nodes in each layer of a 3-layered topology), 1 JLoopix client, and 1 Python Loopix client. -->