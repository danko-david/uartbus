#!/bin/bash

# usage: ./compile_and_upload.sh $mcu $bus_address $ttyUSB$number
# example: ./compile_and_upload.sh atmega328p 4 1

set -e

avr-g++ -o ubb.o ub_bootloader.c ub_atmega.c ../bus/lib/common/ub.c ../utils/lib/rpc/rpc.c -mmcu=$1\
	-I../commons/\
	-I../bus/lib/common/\
	-I../utils/lib/rpc/\
	-Os\
	-Wl,--section-start=.bootloader=0x7e00\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-DDONT_USE_SOFT_FP=1\
	-DF_CPU=16000000\
	-DUB_HOST_VERSION=1\
	-DBUS_ADDRESS=$2\
	-Wl,--section-start=.host_table=0x1fe0\
	-DHOST_TABLE_ADDRESS=0x1fe0\
	-DBAUD_RATE=115200\
	-DF_CPU=16000000\
	-DAPP_START_ADDRESS=0x2000\
	-DAPP_CHECKSUM=0\
	-Wl,--defsym=__stack=0x800700\
	-Wl,--section-start=.data=0x800702\
	-Wl,-Tbss,0x800760


#TODO calculate data, bss, stack adresses
#WARNING: bss, data and stack might collide and NOTHING NOTICES THAT!
# Before you give up your life and get self suicide, beacuse of the random weird behaviors
# recalculate manually these boundary values.
# highest value: 0x800000 + RAMEND (atmega328: 0x8FF)
# RAMEND from /usr/lib/avr/include/avr/iom328p.h
# (no heap used in host)
# order:
#	- stack (*): base
#	- data (80): base + 2
#	- BSS (160): base + 80
# so base +80+160 (240) must not exceed RAMEND
# stack grows down to the direction of the application space

avr-objcopy -O ihex -R .eeprom ubb.o ubb.hex
#wc ubb.hex
size ubb.o
avr-objdump -S --disassemble  ubb.o > ubb.asm
avr-nm --size-sort ubb.o > ubb.sizes

avrdude -p $1 -b 19200 -c avrisp -P /dev/ttyUSB$3 -Uflash:w:ubb.hex:i
