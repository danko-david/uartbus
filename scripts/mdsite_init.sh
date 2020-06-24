#!/bin/bash
cd "$(dirname `readlink -f "$0"`)"
cd ../WD
mkdir -p mdsite
cd mdsite
ln -s ../../resources .
