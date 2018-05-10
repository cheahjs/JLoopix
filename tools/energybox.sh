#!/bin/bash

cp ../external/energybox/build/libs/energybox-2.0.jar .
cp ../external/energybox/build/libs/config/3g_teliasonera.config .
cp ../external/energybox/build/libs/config/nexus_one_3g.config .

for folder in ../results/bandwidth/*; do
    capture="$folder/network.pcap"
    tshark -r $capture -w "$folder/energybox_1.pcap" -Y "udp and udp.port==31001 and sll.pkttype==3"
    java -jar energybox-2.0.jar --t="$folder/energybox_1.pcap" --n=3g_teliasonera.config --d=nexus_one_3g.config --f="$folder/energybox_1" | grep "Total power in Joules" | awk -F " " '{print $5}' > $folder/energy_1.txt
    tshark -r $capture -w "$folder/energybox_2.pcap" -Y "udp and udp.port==31002 and sll.pkttype==3"
    java -jar energybox-2.0.jar --t="$folder/energybox_2.pcap" --n=3g_teliasonera.config --d=nexus_one_3g.config --f="$folder/energybox_2" | grep "Total power in Joules" | awk -F " " '{print $5}' > $folder/energy_2.txt
    tshark -r $capture -w "$folder/energybox_3.pcap" -Y "udp and udp.port==31003 and sll.pkttype==3"
    java -jar energybox-2.0.jar --t="$folder/energybox_3.pcap" --n=3g_teliasonera.config --d=nexus_one_3g.config --f="$folder/energybox_3" | grep "Total power in Joules" | awk -F " " '{print $5}' > $folder/energy_3.txt
    tshark -r $capture -w "$folder/energybox_4.pcap" -Y "udp and udp.port==31004 and sll.pkttype==3"
    java -jar energybox-2.0.jar --t="$folder/energybox_4.pcap" --n=3g_teliasonera.config --d=nexus_one_3g.config --f="$folder/energybox_4" | grep "Total power in Joules" | awk -F " " '{print $5}' > $folder/energy_4.txt
done