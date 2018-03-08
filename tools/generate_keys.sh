#!/bin/bash

MIXNODE_COUNT=6
PROVIDER_COUNT=2
CLIENT_COUNT=6
CWD=$(pwd)

HOST=127.0.0.1

rm -R build

# Generate keys
for ((i=1;i<=PROVIDER_COUNT;i++)); do
    printf "Creating provider $i\n"
    mkdir -p "../build/loopix_keys/provider_$i/"
    (cd "../build/loopix_keys/provider_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_provider.py" $((30000 + $i)) $HOST "provider_$i")
done

for ((i=1;i<=CLIENT_COUNT;i++)); do
    CLIENT_PROVIDER=$((($i % $PROVIDER_COUNT) + 1))
    printf "Creating client $i with provider $CLIENT_PROVIDER\n"
    mkdir -p "../build/loopix_keys/client_$i/"
    (cd "../build/loopix_keys/client_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_client.py" $((31000 + $i)) $HOST "client_$i" "provider_$CLIENT_PROVIDER")
done

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    MIX_LAYER=$((($i-1) % 3))
    printf "Creating mixnode $i in layer $MIX_LAYER\n"
    mkdir -p "../build/loopix_keys/mixnode_$i/"
    (cd "../build/loopix_keys/mixnode_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_mixnode.py" $((32000 + $i)) $HOST "mix_$i" "$MIX_LAYER")
done

# Add nodes to DB
python loopix_db.py