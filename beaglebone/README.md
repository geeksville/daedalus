# Personal notes on avonics project

Getting started doc here: https://github.com/beagleboard/beaglebone-blue/wiki/Frequently-Asked-Questions-(FAQ)#What_system_firmware_should_I_use_for_starting_to_explore_my_BeagleBone_Blue

DONE

* Install Jessie IoT debian from here: http://beagleboard.org/latest-images

* Good info on the extra devices here: http://strawsondesign.com/#!manual-install

# Basic setup

## Debian install process

Per http://elinux.org/Beagleboard:BeagleBoneBlack_Debian
unxz <bone-debian-8.7-iot-armhf-2017-03-19-4gb.img.xz >bone-debian-8.7-iot-armhf-2017-03-19-4gb.img
sudo dd if=bone-debian-8.7-iot-armhf-2017-03-19-4gb.img of=/dev/sdd bs=1M

## shell access

ssh debian@192.168.7.2
psw: temppwd

## i2c

list i2c enabled bus controllers: i2cdetect -l

# Turn off their standard webserver

apt-get remove bone101 nodejs apache2 c9-core-installer
systemctl disable bonescript.service
systemctl disable bonescript.socket
systemctl disable bonescript-autorun.service

## Joining wifi

Follow https://www.digikey.com/en/maker/blogs/how-to-setup-wifi-on-the-beaglebone-black-wireless/f6452fa17bd24347a59f306355ebfef8

sudo connmanctl

connmanctl> enable wifi

Enabled wifi

connmanctl> scan wifi

Scan completed for wifi

connmanctl> services

*AO TNCAPA97AB9

wifi_506583d4fc5e_544e434150413937414239_managed_psk

wifi_506583d4fc5e_hidden_managed_psk

DIRECT-roku-876

wifi_506583d4fc5e_4449524543542d726f6b752d383736_managed_psk

BTHub6-H5H7

wifi_506583d4fc5e_4254487562362d48354837_managed_psk

virginmedia2029431

wifi_506583d4fc5e_76697267696e6d6564696132303239343331_managed_psk

VM046693-2G

wifi_506583d4fc5e_564d3034363639332d3247_managed_psk

BTWifi-with-FON

wifi_506583d4fc5e_4254576966692d776974682d464f4e_managed_none

connmanctl> agent on

Agent registered

connmanctl> connect wifi_506583d4fc5e_544e434150413937414239_managed_psk

Passphrase? xxxxxxxxxxx

connected wifi_506583d4fc5e_544e434150413937414239_managed_psk

connmanctl> quit

You should now be connected to your local wifi. You can check that you have an IP address by typing the following in the terminal window:


Address on my current wifi 192.168.0.7

## Expand sd card image

cd /opt/scripts/tools/
git pull
sudo ./grow_partition.sh
sudo reboot

## Update kernel to 4.4.68

cd /opt/scripts/tools/
git pull
sudo ./update_kernel.sh
sudo reboot
sudo apt-get install linux-headers-`uname -r`

## Setup battery

https://www.element14.com/community/community/designcenter/single-board-computers/next-gen_beaglebone/blog/2013/08/10/bbb--rechargeable-on-board-battery-system

apt-get install acpi # so board will shutdown cleanly on loss of power
but it still needs work - see this thread: https://groups.google.com/forum/#!searchin/beagleboard/battery$20power|sort:relevance/beagleboard/K6rQ7W7zwxs/_WfsdShYAAAJ

# Install gpsd

sudo apt-get install gpsd

# Install canusb Supporting
sudo apt-get install can-utils

for 250kbps
sudo slcand -o -c -f -s5 /dev/ttyUSBX slcan0
sudo ifconfig slcan0 up
candump slcan0

install udev rule and support script once this test is completed
per http://pascal-walter.blogspot.com/2015/08/installing-lawicel-canusb-on-linux.html

# Stratux setup

install Go per https://golang.org/dl/ DO NOT USE version in debian repo

git clone https://github.com/cyoung/stratux.git

export GOROOT=/usr/local/go
export GOPATH=$HOME/go
export PATH=$PATH:/usr/local/go/bin

sudo cp image/rtl-sdr-blacklist.conf /etc/modprobe.d/

sudo apt-get install mercurial cmake

2043  git clone git@github.com:geeksville/librtlsdr.git
2044  cd librtlsdr/
2045  mkdir build
2046  cd build
2055  cmake .. -DINSTALL_UDEV_RULES=ON
2056  make
2057  sudo make install
2058  sudo ldconfig

sudo make install

## for BU-353 GPS

sudo stty -F /dev/ttyUSB0 ispeed 4800
cat /dev/ttyUSB0 # see NMEA stuff

## Start dameon
sudo ./gen_gdl90

## To disable stratux
systemctl disable stratux.service
systemctl stop stratux.service

# Daedalus install
sudo apt-get install oracle-java8-installer
note: openjdk has problems with gradle

# DONE

* get stratux running
* Get debian installed

# TODO

* Get aprs running
* Get ardupilot running
* Investigate this UAVCAN ish effort for a fuel injection controller over canbus: https://github.com/ArduPilot/ardupilot/pull/6438#issuecomment-309283971
* Investigate UAVCAN development by this guy: https://github.com/ArduPilot/ardupilot/pull/6167
