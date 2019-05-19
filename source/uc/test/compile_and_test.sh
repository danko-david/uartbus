#!/bin/bash

rm test.so

set -e

gcc -DUB_TEST -fPIC -shared -g -rdynamic -llct -I../bus/lib/common -I../commons -I../utils/lib/rpc test.c -o test.so

lct ./test.so

