#!/bin/bash

# Should be in sync with generate_keys.sh
MIXNODE_COUNT=6
PROVIDER_COUNT=1
CLIENT_COUNT=2

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

echo "Stopping Java client 1"
$DOCKER_PATH rm -f "client_1" > /dev/null 2>&1

for ((i=2;i<=CLIENT_COUNT;i++)); do
    echo "Stopping client $i"
    $DOCKER_PATH rm -f "client_$i" > /dev/null 2>&1
done