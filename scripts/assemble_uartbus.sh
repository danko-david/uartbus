#!/bin/bash
cd "$(dirname "$0")"
cd ../source/java/uartbus
# GO to the maven directory, package to final jar
mvn package
cd ../../../WD/
cp ../source/java/uartbus/target/uartbus.jar .

