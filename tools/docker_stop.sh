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

for ((i=1;i<=PROVIDER_COUNT;i++)); do
    echo "Stopping provider $i"
    $DOCKER_PATH rm -f "provider_$i" > /dev/null 2>&1
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    echo "Stopping mixnode $i"
    $DOCKER_PATH rm -f "mix_$i" > /dev/null 2>&1
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    echo "Stopping client $i"
    $DOCKER_PATH rm -f "client_$i" > /dev/null 2>&1
done