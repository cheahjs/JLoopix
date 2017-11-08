#!/bin/bash

# Should be in sync with generate_keys.sh
MIXNODE_COUNT=3
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
    echo "Starting provider $i"
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/build/loopix_keys/provider_$i/publicProvider.bin:/loopix/loopix/publicProvider.bin" \
    -v "$DIR/build/loopix_keys/provider_$i/secretProvider.prv:/loopix/loopix/secretProvider.prv" \
    -w "/loopix/loopix" \
    -d \
    deathmax/loopix \
    twistd -ny run_provider.py
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    echo "Starting mixnode $i"
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/build/loopix_keys/mixnode_$i/publicMixnode.bin:/loopix/loopix/publicMixnode.bin" \
    -v "$DIR/build/loopix_keys/mixnode_$i/secretMixnode.prv:/loopix/loopix/secretMixnode.prv" \
    -w "/loopix/loopix" \
    -d \
    deathmax/loopix \
    twistd -ny run_mixnode.py
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    echo "Starting client $i"
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/build/loopix_keys/client_$i/publicClient.bin:/loopix/loopix/publicClient.bin" \
    -v "$DIR/build/loopix_keys/client_$i/secretClient.prv:/loopix/loopix/secretClient.prv" \
    -w "/loopix/loopix" \
    -d \
    deathmax/loopix \
    twistd -ny run_client.py
done
