#!/bin/bash

# usage: ./compile_and_upload.sh $baud $ttyUSB$number
# example: ./compile_and_upload.sh 115200 0

set -e

avr-gcc -o ub_bc.o bus_connector.cpp ../../../bus/lib/common/ub.cpp -mmcu=atmega2560\
	-I../../../bus/lib/common/\
	-Os\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-DF_CPU=16000000\
	-DBAUD_RATE=$1

avr-objcopy -O ihex -R .eeprom ub_bc.o ub_bc.hex

avrdude -p atmega2560 -b 19200 -c avrisp -P /dev/ttyUSB$2 -Uflash:w:ub_bc.hex:i
