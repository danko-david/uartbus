#!/bin/bash
cd "$(dirname `readlink -f "$0"`)"
cd ..
mdsite compile -d doc -t WD/mdsite
