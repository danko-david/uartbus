#!/bin/bash

$(dirname `readlink -f "$0"`)/../source/uc/bootloader/linux_compile.sh "$@"

