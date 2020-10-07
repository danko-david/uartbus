#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

./mdsite_compile.sh

cd ../doc/doxygen
doxygen source.cfg

cd ../../source/java/uartbus/
mvn site

cd ../../../
ln -s $(readlink -f ./WD/doxygen/html/) ./WD/mdsite/documentation/doxygen
ln -s $(readlink -f ./source/java/uartbus/target/site/apidocs/) ./WD/mdsite/documentation/javadoc
ln -s $(readlink -f ./source/java/uartbus/target/site/testapidocs/) ./WD/mdsite/documentation/test-javadoc

