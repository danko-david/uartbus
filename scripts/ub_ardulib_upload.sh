#!/bin/bash

set -e

if [ "$#" -lt 5 ]; then
	echo 'Usage: ./ub_ardulib_upload.sh $MCU_TYPE $CPU_SPEED $DEVICE_ADDRESS $APP_NAME $...least one source file and optional compilation agruments'
	echo 'eg: ./compile_and_upload.sh atmega328p 16000000 4 myapp    myapp.c ... mylib.c -I/usr/share/mylibs -AIRemote -ATM1637 -DCOMPILE_FLAG_DEBUG=1 -Wall'
	exit 1
fi

compile_ub_arduino_app_with_selective_arduino_libs.sh $1 $2 $4 "${@:5}"

ub upload -c $4.hex -t $3

