#!/bin/bash

rm test.so

set -e

gcc -DUB_TEST -fPIC -shared -g -rdynamic -llct -I../bus/lib/common -I../commons -I../utils/lib/rpc test_uartbus.c -o test_uartbus.so

lct ./test_uartbus.so

