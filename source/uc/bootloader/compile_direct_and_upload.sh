#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

if [ "$#" -lt 4 ]; then
	echo 'Usage: ./compile_direct_and_upload.sh $mcu $baud_rate $bus_address $ttyUSBnumber'
	echo 'eg: ./compile_direct_and_upload.sh atmega328p 115200 4 1'
	exit 1
fi

set -e

if [ -z "$F_CPU" ]; then
	F_CPU=16000000
fi

./compile_and_upload_for_uc.sh $1 $2 $3 $4 -DUB_EXCLUDE_UARTBUS_HEADER ub_bootloader.c ub_atmega.c direct.c

