#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

./CI_build_tests.sh
./CI_run_test_tests.sh

