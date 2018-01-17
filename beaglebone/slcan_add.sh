#!/bin/sh
# Bind the USBCAN device
slcand -o -c -f -s 5 /dev/$1 slcan0
sleep 2
ifconfig slcan0 up
