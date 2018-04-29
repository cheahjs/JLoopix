#!/bin/bash

#############################
# Bandwidth data collection #
#############################

# Rate parameters
RATE_START=0.1
RATE_END=3.0
RATE_TICK=0.1
DELAY=0.001

# Network parameters
JAVA_COUNT=100
PYTHON_COUNT=100
MIX_COUNT=6
PROVIDER_COUNT=4

# Initial setup for creating keys
python setup_network.py \
    --mix $MIX_COUNT --provider $PROVIDER_COUNT --client $PYTHON_COUNT --client-java $JAVA_COUNT
./generate_keys.sh

# Collect bandwidth data
for rate in $(seq $RATE_START $RATE_TICK $RATE_END); do
    echo "Running at rate $rate"
    # Setup network config
    python setup_network.py \
        --rate-real $rate --rate-drop $rate --rate-loop $rate --delay $DELAY \
        --mix $MIX_COUNT --provider $PROVIDER_COUNT --client $PYTHON_COUNT --client-java $JAVA_COUNT
    # Run docker containers
    ./docker_run.sh
    # Gather bandwidth data
    ./gather_bandwidth_data.sh 180s
    # Stop containers
    ./docker_stop.sh
done

###########################
# Latency data collection #
###########################

PROVIDER_COUNT=4
MIX_COUNT=6
CLIENT_START=10
CLIENT_END=100
CLIENT_TICK=15

# Collect zero delay latency data
for count in $(seq $CLIENT_START $CLIENT_TICK $CLIENT_END); do
    echo "Running with count $count"
    # Setup network config
    python setup_network.py \
        --rate-real 2.67 --rate-drop 2.66 --rate-loop 2.67 --delay 0 \
        --mix $MIX_COUNT --provider $PROVIDER_COUNT --client $count --client-java $count \
        --push true
    # Generate keys since we've change the number of clients
    ./generate_keys.sh
    # Run docker containers
    ./docker_run_latency.sh
    # Gather latency data
    ./gather_latency_data.sh 180s
    # Stop containers
    ./docker_stop.sh
done

# Collect total latency data
echo "Running total latency data with 200 clients"
# Setup network config
python setup_network.py \
    --rate-real 2.67 --rate-drop 2.66 --rate-loop 2.67 --delay 0 \
    --mix $MIX_COUNT --provider $PROVIDER_COUNT --client 100 --client-java 100 \
    --max-retrieve 30 --time-pull 5
# Generate keys since we've change the number of clients
./generate_keys.sh
# Run docker containers
./docker_run_latency.sh
# Gather latency data
./gather_total_latency_data.sh 180s
# Stop containers
./docker_stop.sh