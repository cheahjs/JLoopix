#!/bin/bash

python setup_network.py \
    --rate-real 2.67 --rate-drop 2.66 --rate-loop 2.67 --delay 0 \
    --mix 6 --provider 2 --client 0 --client-java 2 \
    --max-retrieve 20 --time-pull 1

./generate_keys.sh
./docker_run_chat.sh