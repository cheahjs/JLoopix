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
    echo "Starting provider $i"
    $DOCKER_PATH rm -f "provider_$i" > /dev/null 2>&1
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/../build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/../build/loopix_keys/provider_$i/publicProvider.bin:/loopix/loopix/publicProvider.bin" \
    -v "$DIR/../build/loopix_keys/provider_$i/secretProvider.prv:/loopix/loopix/secretProvider.prv" \
    -v "$DIR/../build/loopix_config.json:/config.json" \
    -w "/loopix/loopix" \
    -dit \
    --name="provider_$i" \
    deathmax/loopix \
    twistd --nodaemon --python=run_provider.py
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    echo "Starting mixnode $i"
    $DOCKER_PATH rm -f "mix_$i" > /dev/null 2>&1
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/../build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/../build/loopix_keys/mixnode_$i/publicMixnode.bin:/loopix/loopix/publicMixnode.bin" \
    -v "$DIR/../build/loopix_keys/mixnode_$i/secretMixnode.prv:/loopix/loopix/secretMixnode.prv" \
    -v "$DIR/../build/loopix_config.json:/config.json" \
    -w "/loopix/loopix" \
    -dit \
    --name="mix_$i" \
    deathmax/loopix \
    twistd --nodaemon --python=run_mixnode.py
done

for ((i=1;i<=JAVA_COUNT;i++)); do
    echo "Starting Java chat client $i"
    $DOCKER_PATH rm -f "client_$i" > /dev/null 2>&1
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/../build/example.db:/example.db" \
    -v "$DIR/../build/loopix_keys/client_$i/publicClient.bin:/publicClient.bin" \
    -v "$DIR/../build/loopix_keys/client_$i/secretClient.prv:/secretClient.prv" \
    -v "$DIR/../build/jloopix_config.json:/config.json" \
    -w "/" \
    -dit \
    --entrypoint="/bin/sh" \
    --name="client_$i" \
    deathmax/jloopix \
    "-c" \
    "/usr/bin/java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Done-jar.main.class=me.jscheah.jloopix.client.latencymeasurement.LatencyMeasurement -jar /jloopix.jar config.json publicClient.bin secretClient.prv"
done

for ((i=(JAVA_COUNT+1);i<=(CLIENT_COUNT+JAVA_COUNT);i++)); do
    echo "Starting client $i"
    $DOCKER_PATH rm -f "client_$i" > /dev/null 2>&1
    $DOCKER_PATH run \
    --net=host \
    -v "$DIR/../build/example.db:/loopix/loopix/example.db" \
    -v "$DIR/../build/loopix_keys/client_$i/publicClient.bin:/loopix/loopix/publicClient.bin" \
    -v "$DIR/../build/loopix_keys/client_$i/secretClient.prv:/loopix/loopix/secretClient.prv" \
    -v "$DIR/../build/loopix_config.json:/loopix/loopix/config.json" \
    -w "/loopix/loopix" \
    -dit \
    --name="client_$i" \
    deathmax/loopix \
    twistd --nodaemon --python=run_client.py
done
