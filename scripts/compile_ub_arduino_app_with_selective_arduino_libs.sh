#!/bin/bash
# usage: ./compile_ub_arduino_app_with_arduino_libs.sh $mcu $clock_hz $output_nameset $gcc_options_and_cpp_file
# eg: ./compile_ub_arduino_app_with_arduino_libs.sh atmega328p 16000000 myprog    myprog.cpp -o 

SOURCES=()
INCLUDES=()
ETC=()
for i in "$@"
do
	if [[ $i == -A* ]]; then
		l=${i##*-A}
		if [ -d "/usr/share/arduino/libraries/$l" ] || [ -L "/usr/share/arduino/libraries/$l" ]; then
			INCLUDES+=("-I/usr/share/arduino/libraries/$l")
			while read -r line; do
				SOURCES+=("$line")
			done <<< `ls $l/*.c{,pp}`
		fi

		CANON=`readlink -f ~/Arduino/libraries/$l`

		if [ -d "$CANON" ] ; then
			INCLUDES+=("-I$CANON")
		fi

		while read -r line; do
			SOURCES+=("$line")
		done <<< `ls $CANON/*.c{,pp}`

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
