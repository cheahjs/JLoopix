#!/bin/bash

# If we're running on Bash on Windows, use docker.exe instead of docker
if grep -q Microsoft /proc/version; then
    DOCKER_PATH="docker.exe"
else
    DOCKER_PATH="docker"
fi

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

# Build single Loopix image
$DOCKER_PATH build \
    -t deathmax/loopix \
    -f docker/Dockerfile \
    .

# Build JLoopix image
cd jloopix
$DOCKER_PATH build \
    -t deathmax/jloopix \
    .
