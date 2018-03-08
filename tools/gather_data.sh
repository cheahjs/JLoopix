#!/bin/bash

# Usage
# ./gather_data.sh

MIXNODE_COUNT=6
PROVIDER_COUNT=2
CLIENT_COUNT=6

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

echo "Gathering data for network into folder results/$DATE"

mkdir results/$DATE
mkdir results/$DATE/logs/
cp -R build/loopix_keys/ results/$DATE/keys/
cp build/jloopix_config.json results/$DATE/config.json

$DOCKER_PATH run --name="gather" --rm -d \
    -v "$DIR/../results:/data" --net=host \
    marsmensch/tcpdump -i any \
    "udp" \
    -w "/data/$DATE/network.pcap"
    # "port $((31000 + $1))" and "udp" \

sleep 300

$DOCKER_PATH stop gather
for ((i=1;i<=PROVIDER_COUNT;i++)); do
    $DOCKER_PATH logs "provider_$i" > "results/$DATE/logs/provider_$i"
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    $DOCKER_PATH logs "mix_$i" > "results/$DATE/logs/mix_$i"
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    $DOCKER_PATH logs "client_$i" > "results/$DATE/logs/client_$i"
done