#!/bin/bash
# usage: ./compile_ub_arduino_app_with_arduino_libs.sh $mcu $clock_hz $output_nameset $gcc_options_and_cpp_file
# eg: ./compile_ub_arduino_app_with_arduino_libs.sh atmega328p 16000000 myprog    myprog.cpp -o 

SOURCES=(~/Arduino/libraries/*/*.cpp)
#SOURCES=(~/Arduino/libraries/*/*.c{,pp} /usr/share/arduino/libraries/*/*.c{,pp})
#echo ${SOURCES[*]}

INCLUDES=()
while read -r line; do
	INCLUDES+=("-I$line")
done <<< `find  ~/Arduino/libraries/ -maxdepth 1 -xtype d`
#done <<< `find  ~/Arduino/libraries/ -maxdepth 1 -xtype d && find /usr/share/arduino/libraries -maxdepth 1 -xtype d`

#echo ${INCLUDES[*]}

args=("$@")
args+=("-DARDUINO=200")
args+=("${INCLUDES[@]}")
args+=("${SOURCES[@]}")

C_SCRIPT=`readlink -f $(dirname "$0")/compile_ub_arduino_app.sh`
#echo $C_SCRIPT
#echo ${args[@]}
$($C_SCRIPT "${args[@]}")
