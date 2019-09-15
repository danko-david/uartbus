#!/bin/bash
#usage: target_atmega2560_make_and_upload.sh $BAUD $ttyUSB$num
#example target_atmega2560_make_and_upload.sh 115200 0 //this upload to adruino attached to /dev/ttyUSB0
make clean
set -e
MCU=atmega2560 F_CPU=16000000 ARDUINO_VARIANT=mega EXT_FLAGS="-DBAUD=$1 -DPC_SERIAL0 -DBUS_SERIAL3 -D__AVR_ATmega2560P__" make

avrdude -p atmega2560 -b115200 -c arduino -P /dev/ttyUSB$2 -cwiring -D -Uflash:w:uartbus_connector.cpp.hex:i

