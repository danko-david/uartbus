#!/bin/bash

gcc -o ubh_linux ub_bootloader.c ub_linux.c ../bus/lib/common/ub.c ../bus/lib/addressing/addr16.c ../utils/lib/rpc/rpc.c\
	-I../commons/\
	-I../bus/lib/common/\
	-I../bus/lib/addressing/\
	-I../utils/lib/rpc/\
	-Os\
	-ffunction-sections\
	-fdata-sections\
	-fno-exceptions\
	-lpthread\
	-DUBH_CALLBACK_ENQUEUE_PACKET\
	-DUB_HOST_VERSION=1\
	-DAPP_START_ADDRESS=0x0\
	-DBUS_ADDRESS=ubh_linux_get_bus_address\(\)\
