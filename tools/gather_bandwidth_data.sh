#!/bin/bash

# Usage
# ./gather_bandwidth_data.sh

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

echo "Gathering data for network into folder ../results/bandwidth/$DATE"

mkdir -p ../results/bandwidth/$DATE
mkdir -p ../results/bandwidth/$DATE/logs
cp -R ../build/loopix_keys/ ../results/bandwidth/$DATE/keys/
cp ../build/jloopix_config.json ../results/bandwidth/$DATE/config.json

$DOCKER_PATH run --name="gather" --rm -d \
    -v "$DIR/../results:/data" --net=host \
    marsmensch/tcpdump -i any -B 131072 \
    -w "/data/bandwidth/$DATE/network.pcap" \
    udp and portrange 30000-34000 and src net 172.0.0.0/8

sleep 300

$DOCKER_PATH stop gather
for ((i=1;i<=PROVIDER_COUNT;i++)); do
    $DOCKER_PATH logs "provider_$i" > "../results/bandwidth/$DATE/logs/provider_$i"
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    $DOCKER_PATH logs "mix_$i" > "../results/bandwidth/$DATE/logs/mix_$i"
done

for ((i=1;i<=(CLIENT_COUNT+JAVA_COUNT);i++)); do
    $DOCKER_PATH logs "client_$i" > "../results/bandwidth/$DATE/logs/client_$i"
done