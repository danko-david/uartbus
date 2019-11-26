#!/bin/bash

ROOT=$(dirname `readlink -f "$0"`)"/"
echo $ROOT

gcc -o ubh_linux ${ROOT}ub_bootloader.c ${ROOT}ub_linux.c ${ROOT}../bus/lib/common/ub.c ${ROOT}../bus/lib/addressing/addr16.c ${ROOT}../utils/lib/rpc/rpc.c\
	-I${ROOT}../commons/\
	-I${ROOT}../bus/lib/common/\
	-I${ROOT}../bus/lib/addressing/\
	-I${ROOT}../utils/lib/rpc/\
	-Os\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-lpthread\
	-DUBH_CALLBACK_ENQUEUE_PACKET\
	-DUB_HOST_VERSION=1\
	-DAPP_START_ADDRESS=0x0\
	-DBUS_ADDRESS=ubh_linux_get_bus_address\(\)\
	"$@"
