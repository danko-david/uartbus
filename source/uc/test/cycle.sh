#!/bin/bash
rm lct_result_*.junit.xml
./compile_and_test.sh
cat lct_result*
