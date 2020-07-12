#!/bin/bash
#usage: target_atmega328_make_and_upload.sh $BAUD $ttyUSB$num
#example target_atmega328_make_and_upload.sh 115200 0 //this upload to adruino attached to /dev/ttyUSB0
make clean
set -e

if [ "$#" -lt 2 ]; then
        echo 'Usage: ./target_atmega328_make_and_upload.sh $BAUD $ttyUSBnum'
        echo 'eg: ./target_atmega328_make_and_upload.sh 115200 0'
        exit 1
fi

MCU=atmega328p F_CPU=16000000 ARDUINO_VARIANT=standard EXT_FLAGS="-DBAUD=$1 -DPC_SERIAL_SOFT -DBUS_SERIAL0 -D__AVR_ATmega328P__" make

#upload using arduino bootloader on the chip' board
#avrdude -p atmega328p -b19200 -c arduino -P /dev/ttyUSB$2 -cwiring -D -Uflash:w:uartbus_connector.cpp.hex:i

#upload directoy using arduino as an isp

if [ $2 -gt -1 ]; then
	avrdude -p atmega328p -b19200 -c avrisp -P /dev/ttyUSB$2 -Uflash:w:uartbus_connector.cpp.hex:i
fi

