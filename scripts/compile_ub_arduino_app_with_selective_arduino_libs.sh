#!/bin/bash
# usage: ./compile_ub_arduino_app_with_arduino_libs.sh $mcu $clock_hz $output_nameset $gcc_options_and_cpp_file
# eg: ./compile_ub_arduino_app_with_arduino_libs.sh atmega328p 16000000 myprog    myprog.cpp -o

if [ "$#" -lt 3 ]; then
	echo 'Usage: ./compile_ub_arduino_app_with_arduino_libs.sh $mcu $clock_hz $output_nameset $gcc_options_and_cpp_file'
	echo 'eg: ./compile_ub_arduino_app_with_arduino_libs.sh atmega328p 16000000 myprog myprog.cpp -o'
        exit 1
fi

if [ -z ${ARDUINO_LIB+x} ]; then
#	ARDUINO_LIB=/usr/share/arduino/hardware/arduino/
	ARDUINO_LIB=/usr/share/arduino/
fi

SOURCES=()
INCLUDES=()
ETC=()
for i in "$@"
do
	if [[ $i == -A* ]]; then
		# read library from the system location

		l=${i##*-A}
		if [ -d "$ARDUINO_LIB/libraries/$l" ] || [ -L "$ARDUINO_LIB/libraries/$l" ]; then
			INCLUDES+=("-I$ARDUINO_LIB/libraries/$l")
			INCLUDES+=("-I$ARDUINO_LIB/libraries/$l/src")
			while read -r line; do
				SOURCES+=("$line")
			done <<< `ls $ARDUINO_LIB/libraries/$l{,/src}/*.c{,pp}`
		fi

		# read library from your home location

		CANON=`readlink -e ~/Arduino/libraries/$l`

		if [ -d "$CANON" ] ; then
			INCLUDES+=("-I$CANON")
			INCLUDES+=("-I$CANON/src/")

			while read -r line; do
				SOURCES+=("$line")
			done <<< `ls $CANON{,/src}/*.c{,pp}`
		fi

	else
		ETC+=("$i")
	fi
done

#echo ${SOURCES[*]}
#echo ${INCLUDES[*]}

args=()
args+=("${ETC[@]}")
args+=("-DARDUINO=200")
args+=("${INCLUDES[@]}")
args+=("${SOURCES[@]}")

C_SCRIPT=`readlink -f $(dirname "$0")/compile_ub_arduino_app.sh`
#echo $C_SCRIPT

echo ${args[@]}
#exit
$($C_SCRIPT "${args[@]}")
