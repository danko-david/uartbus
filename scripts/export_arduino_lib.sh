#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"
cd ../WD

mkdir -p arduino_ub_lib

rm -R arduino_ub_lib/*

set -e

cd arduino_ub_lib

cp ../../source/uc/commons/posix_errno.h .
cp ../../source/uc/bus/lib/common/ub.c ub.cpp
cp ../../source/uc/bus/lib/common/ub.h .
cp ../../source/uc/bus/lib/addressing/addr16.* .

cp -R ../../source/uc/bus/lib/arduino/* .
