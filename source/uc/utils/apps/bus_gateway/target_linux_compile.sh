#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

rm linux_uartbus_gateway

gcc -g\
	../../../bus/lib/common/ub.c\
	../../../../../deps/toe/src/c/utils.c\
	../../../../../deps/toe/src/c/concurrency.c\
	../../../../../deps/toe/src/c/rerunnable_thread.c\
	../../../../../deps/toe/src/c/worker_pool.c\
	-I../../../../../deps/toe/src/c/\
	-I../../../bus/lib/common/\
	linux_bus_gateway.c\
	-lpthread\
	-o linux_uartbus_gateway
