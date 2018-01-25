# JLoopix
[![Build Status](https://travis-ci.org/cheahjs/JLoopix.svg?branch=master)](https://travis-ci.org/cheahjs/JLoopix)

A Java library that implements the [Loopix anonymous communication system](https://arxiv.org/abs/1703.00536).

Based off the original [Python implementation](https://github.com/UCL-InfoSec/loopix).

## Building

1. Clone the repo and submodules `git clone --recursive https://github.com/cheahjs/JLoopix`
2. `./build_jar.sh` to build JLoopix's JAR file located under `jloopix/build/libs/jloopix-standalone.jar`
3. `./generate_keys.sh` to generate pub/priv keys and database.
4. `./docker_build.sh` to build the Docker image.

## Running

1. `./docker_run.sh` to run a network with 1 provider, 6 mix nodes (2 nodes in each layer of a 3-layered topology), 1 JLoopix client, and 1 Python Loopix client.