#!/bin/bash

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

TOTAL_CLIENTS=$((CLIENT_COUNT+JAVA_COUNT))
echo "Stopping loopix containers"
eval $DOCKER_PATH rm -f provider_{1..$PROVIDER_COUNT} mix_{1..$MIXNODE_COUNT} client_{1..$TOTAL_CLIENTS} > /dev/null 2>&1