#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"
./CI_hooks_tests.sh build
./CI_hooks_tests.sh test

