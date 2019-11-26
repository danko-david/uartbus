#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

set -e

./ci_execute.sh clean
./ci_execute.sh prepare_build
./ci_execute.sh build
./ci_execute.sh test
