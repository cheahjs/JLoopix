#!/bin/bash

# Usage
# ./gather_total_latency_data.sh

# Import network counts
source network_config.sh

# If we're running on Bash on Windows, use docker.exe instead of docker
if grep -q Microsoft /proc/version; then
    DOCKER_PATH="docker.exe"
    # Convert current directory to Windows style paths
    DIR=$(echo "$(pwd)" | sed -e 's|/mnt/\(.\)/|\1\:/|g')
else
    DOCKER_PATH="docker"
    DIR=$(pwd)
fi

DATE=`date +%Y-%m-%d-%H-%M-%S`

echo "Gathering data for total latency into folder ../results/latency_total/$DATE"

mkdir -p ../results/latency_total/$DATE
mkdir -p ../results/latency_total/$DATE/logs/
cp ../build/jloopix_config.json ../results/latency_total/$DATE/config.json

sleep 180

# $DOCKER_PATH stop gather
for ((i=1;i<=PROVIDER_COUNT;i++)); do
    $DOCKER_PATH logs "provider_$i" > "../results/latency_total/$DATE/logs/provider_$i"
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    $DOCKER_PATH logs "mix_$i" > "../results/latency_total/$DATE/logs/mix_$i"
done

for ((i=1;i<=(CLIENT_COUNT+JAVA_COUNT);i++)); do
    $DOCKER_PATH logs "client_$i" > "../results/latency_total/$DATE/logs/client_$i"
done

$DOCKER_PATH cp client_1:/latency.csv ../results/latency/$DATE/latency.csv
$DOCKER_PATH cp client_2:/latency.csv ../results/latency/$DATE/latency_2.csv
$DOCKER_PATH cp client_3:/latency.csv ../results/latency/$DATE/latency_3.csv