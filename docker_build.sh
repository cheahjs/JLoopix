#!/bin/bash

# If we're running on Bash on Windows, use docker.exe instead of docker
if grep -q Microsoft /proc/version; then
    DOCKER_PATH="docker.exe"
else
    DOCKER_PATH="docker"
fi

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
