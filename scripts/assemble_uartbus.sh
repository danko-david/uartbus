#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"
cd ../source/java/uartbus
# GO to the maven directory, package to final jar
mvn compile
mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec > target/classes/eu/javaexperience/electronic/uartbus/version
mvn package
cd ../../../WD/
cp ../source/java/uartbus/target/uartbus.jar .

