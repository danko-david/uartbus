#!/bin/bash

rm *.so

set -e

gcc -DUB_TEST -fPIC -shared -g -rdynamic -llct -I../bus/lib/common -I../commons -I../utils/lib/rpc test_uartbus.c -o test_uartbus.so
gcc -DUB_TEST -DUBH_TEST -fPIC -shared -g -rdynamic -llct -I../bus/lib/common -I../commons -I../utils/lib/rpc ../utils/lib/rpc/rpc.c test_ubh.c -o test_ubh.so

lct ./test_uartbus.so ./test_ubh.so

