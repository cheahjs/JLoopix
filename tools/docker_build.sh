#!/bin/bash

# If we're running on Bash on Windows, use docker.exe instead of docker
if grep -q Microsoft /proc/version; then
    DOCKER_PATH="docker.exe"
else
    DOCKER_PATH="docker"
fi

# Build Loopix (python) image
cd ../external
$DOCKER_PATH build \
    -t deathmax/loopix \
    .

# Build JLoopix image
cd ../src/jloopix
$DOCKER_PATH build \
    -t deathmax/jloopix \
    .