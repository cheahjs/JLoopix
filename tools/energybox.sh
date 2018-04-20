#!/bin/bash

cp ../external/energybox/build/libs/energybox-2.0.jar .
cp ../external/energybox/build/libs/config/3g_teliasonera.config .
cp ../external/energybox/build/libs/config/nexus_one_3g.config .

for folder in ../results/bandwidth/*; do
    capture="$folder/network.pcap"
    tshark -r $capture -w "$folder/energybox.pcap" -Y "udp and udp.port==31001 and sll.pkttype==3"
    java -jar energybox-2.0.jar --t="$folder/energybox.pcap" --n=3g_teliasonera.config --d=nexus_one_3g.config --f="$folder/energybox" | grep "Total power in Joules" | awk -F " " '{print $5}' > $folder/energy.txt
done