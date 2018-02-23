#!/bin/bash

# Usage
# ./gather_data.sh client_number


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

echo "Gathering data for network into file results/$DATE.pcap"

# cp build/loopix_keys/client_$1/secretClient.prv results/$1_$DATE.prv
mkdir results/$DATE
cp -R build/loopix_keys/ results/$DATE/keys/
cp build/jloopix_config.json results/$DATE/config.json

$DOCKER_PATH run --name="gather" --rm -d \
    -v "$DIR/results:/data" --net=host \
    marsmensch/tcpdump -i any \
    "udp" \
    -w "/data/$DATE/network.pcap"
    # "port $((31000 + $1))" and "udp" \