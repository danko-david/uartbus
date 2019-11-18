#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"
cd ../WD
mkdir arduino_ub_lib 

set -e

cd arduino_ub_lib

cp ../../source/uc/commons/posix_errno.h .
cp ../../source/uc/bus/lib/common/ub.c ub.cpp
cp ../../source/uc/bus/lib/common/ub.h .
cp ../../source/uc/bus/lib/arduino/ub_arduino.c ub_arduino.cpp
cp ../../source/uc/bus/lib/arduino/ub_arduino.h ub_arduino.h
