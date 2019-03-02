#!/bin/bash

# usage: ./compile_and_upload.sh $mcu $bus_address $ttyUSB$number
# example: ./compile_and_upload.sh atmega328p 4 1

set -e

avr-gcc -o ubb.o ub_bootloader.cpp ../bus/lib/common/ub.cpp -mmcu=$1\
	-I../bus/lib/common/\
	-Os\
	-Wl,--section-start=.bootloader=0x7e00\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-DDONT_USE_SOFT_FP=1\
	-DF_CPU=16000000\
	-DUB_HOST_VERSION=1\
	-DBUS_ADDRESS=$2\
	-Wl,--section-start=.host_table=0x4000\
	-DHOST_TABLE_ADDRESS=0x4000\
	-DAPP_START_ADDRESS=0x3000\
	-DAPP_CHECKSUM=0


avr-objcopy -O ihex -R .eeprom ubb.o ubb.hex
wc ubb.hex
avr-objdump -S --disassemble  ubb.o > ubb.asm
avr-nm --size-sort ubb.o > ubb.sizes

avrdude -p $1 -b 19200 -c avrisp -P /dev/ttyUSB$3 -Uflash:w:ubb.hex:i
