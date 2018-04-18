#!/bin/bash

# Import network counts
source network_config.sh
CWD=$(pwd)

HOST=127.0.0.1

rm -R ../build/loopix_keys
rm ../build/example.db

# Generate keys
provider() {
    local i=$1
    printf "Creating provider $i\n"
    mkdir -p "../build/loopix_keys/provider_$i/"
    (cd "../build/loopix_keys/provider_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_provider.py" $((30000 + $i)) "provider_$i" "provider_$i")
}

for ((i=1;i<=PROVIDER_COUNT;i++)); do
    provider $i &
done

client() {
    local i=$1
    local CLIENT_PROVIDER=$((($i % $PROVIDER_COUNT) + 1))
    printf "Creating client $i with provider $CLIENT_PROVIDER\n"
    mkdir -p "../build/loopix_keys/client_$i/"
    (cd "../build/loopix_keys/client_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_client.py" $((31000 + $i)) "client_$i" "client_$i" "provider_$CLIENT_PROVIDER")
}

for ((i=1;i<=(CLIENT_COUNT+JAVA_COUNT);i++)); do
    client $i &
done

mix() {
    local i=$1
    local MIX_LAYER=$((($i-1) % 3))
    printf "Creating mixnode $i in layer $MIX_LAYER\n"
    mkdir -p "../build/loopix_keys/mixnode_$i/"
    (cd "../build/loopix_keys/mixnode_$i/" && \
        python "$CWD/../external/loopix/loopix/setup_mixnode.py" $((32000 + $i)) "mix_$i" "mix_$i" "$MIX_LAYER")
}

for ((i=1;i<=MIXNODE_COUNT;i++)); do
    mix $i &
done

wait

# Add nodes to DB
python loopix_db.py