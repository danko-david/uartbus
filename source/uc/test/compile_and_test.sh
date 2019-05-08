#!/bin/bash

rm test.o
set -e

gcc -DUB_TEST $(pkg-config --cflags novaprova) $(pkg-config --libs novaprova) -g -gdwarf-3 -I../bus/lib/common -I../commons -I../utils/lib/rpc test.cpp /usr/local/lib/libnovaprova.a -o test.o
#gcc $(pkg-config --cflags novaprova) $(pkg-config --libs novaprova) -g -gdwarf-3 test.cpp -o test.o
#gcc $(pkg-config --cflags novaprova) $(pkg-config --libs novaprova) -g -gdwarf-3 test.cpp -o test.o

./test.o

