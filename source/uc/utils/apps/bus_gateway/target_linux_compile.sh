#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

rm linux_uartbus_gateway

gcc -g\
	../../../bus/lib/common/ub.c\
	../../../../../WD/toe/src/c/utils.c\
	../../../../../WD/toe/src/c/concurrency.c\
	../../../../../WD/toe/src/c/rerunnable_thread.c\
	../../../../../WD/toe/src/c/worker_pool.c\
	-I../../../../../WD/toe/src/c/\
	-I../../../bus/lib/common/\
	linux_bus_gateway.c\
	-lpthread\
	-o linux_uartbus_gateway\
	-DUBG_DEBUG_PRINT
