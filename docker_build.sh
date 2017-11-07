#!/bin/bash

# If we're running on Bash on Windows, use docker.exe instead of docker
if grep -q Microsoft /proc/version; then
    DOCKER_PATH="docker.exe"
else
    DOCKER_PATH="docker"
fi

MIXNODE_COUNT=3
PROVIDER_COUNT=1
CLIENT_COUNT=2
CWD=$(pwd)

# Generate keys
for ((i=1;i<=PROVIDER_COUNT;i++)); do
    printf "Creating provider $i\n"
    mkdir -p "build/loopix_keys/provider_$i/"
    (cd "build/loopix_keys/provider_$i/" && \
        python "$CWD/external/loopix/loopix/setup_provider.py" $((7000 + $i)) "127.0.0.1" "provider_$i")
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    CLIENT_PROVIDER=$((($i % $PROVIDER_COUNT) + 1))
    printf "Creating client $i with provider $CLIENT_PROVIDER\n"
    mkdir -p "build/loopix_keys/client_$i/"
    (cd "build/loopix_keys/client_$i/" && \
        python "$CWD/external/loopix/loopix/setup_client.py" $((8000 + $i)) "127.0.0.1" "client_$i" "provider_$CLIENT_PROVIDER")
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    MIX_LAYER=$(($i % 3))
    printf "Creating mixnode $i in layer $MIX_LAYER\n"
    mkdir -p "build/loopix_keys/mixnode_$i/"
    (cd "build/loopix_keys/mixnode_$i/" && \
        python "$CWD/external/loopix/loopix/setup_mixnode.py" $((9000 + $i)) "127.0.0.1" "mix_$i" "$MIX_LAYER")
done

# Add nodes to DB
python loopix_db.py

# # Build client image
# $DOCKER_PATH build \
#     -t deathmax/loopix-client \
#     -f docker/loopix_client/Dockerfile \
#     .

# # Build provider image
# $DOCKER_PATH build \
#     -t deathmax/loopix-provider \
#     -f docker/loopix_provider/Dockerfile \
#     .

# # Build mixnode image
# $DOCKER_PATH build \
#     -t deathmax/loopix-mixnode \
#     -f docker/loopix_mixnode/Dockerfile \
#     .

# Build single image
$DOCKER_PATH build \
    -t deathmax/loopix \
    -f docker/Dockerfile \
    .