#!/bin/bash

#
# usage: ./compile.sh $mcu $
# eg: ./compile.sh atmega328p /tmp/my_little_app.c

#!/bin/bash

set -e
#avr-gcc -Wl,--section-start=.text=0x3e00 -Wl,--section-start=.app=0x0000 -Wl,--section-start=.version=0x3ffe -mmcu=atmega328p -Os -o ubb.o ub_bootloader.cpp ub.cpp -DF_CPU=16000000
#notice: function have to aligned to even address, otherwise it can be linked
avr-gcc -mmcu=$1 \
	-I"$(dirname "$0")"\
	-DF_CPU=16000000\
	-DHOST_TABLE_ADDRESS=0x1fe0\
	-Wl,--section-start=.text=0x2020\
	-Wl,--section-start=.app_start=0x2000\
	-Os -o app.o $2 ub_app_wrapper.cpp

avr-objcopy -R .eeprom -O ihex app.o app.hex
avr-objdump -S --disassemble  app.o > app.asm
avr-nm --size-sort app.o > app.sizes
