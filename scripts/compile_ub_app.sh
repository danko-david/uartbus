#!/bin/bash
# usage: ./compile_ub_app.sh $mcu $clock_hz $output_nameset $gcc_options_and_cpp_file
# eg: ./compile_ub_app.sh atmega328p 16000000 myprog    myprog.cpp -o 

OWD=$(pwd)
cd "$(dirname "$0")"
cd ../source/uc

I_COMM=$(readlink -f commons)
I_BUSCOMM=$(readlink -f bus/lib/common)
I_RPC=$(readlink -f utils/lib/rpc)
I_WRAP=$(readlink -f utils/ub_app/)
C_RPC=$(readlink -f utils/lib/rpc/rpc.cpp)
C_WRAP=$(readlink -f utils/ub_app/ub_app_wrapper.cpp)
#I_=$(readlink -f )
#I_=$(readlink -f )

cd $OWD

avr-gcc -mmcu=$1\
	-I$I_COMM -I$I_BUSCOMM -I$I_RPC -I$I_WRAP\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-DF_CPU=$2\
	-DHOST_TABLE_ADDRESS=0x1fe0\
	-Wl,--section-start=.text=0x2020\
	-Wl,--section-start=.app_start=0x2000\
	-Os -o $3.o  $C_RPC $C_WRAP "${@:4:999}"

avr-objcopy -O ihex -R .eeprom $3.o $3.hex
avr-objdump -S --disassemble  $3.o > $3.asm
avr-nm --size-sort $3.o > $3.sizes

