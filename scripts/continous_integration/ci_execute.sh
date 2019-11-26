#!/bin/bash

if [ "$#" -lt 1 ]; then
	echo 'Usage: ./ci_execute.sh $command'
	echo 'eg: ./ci_execute.sh build'
	echo 'currently known cycles are: clean prepare_build build test deploy install'
	exit 1
fi

set -e

cd "$(dirname `readlink -f "$0"`)"
cd ../..

find . -type f -name "CI_hooks_*.sh" | while read SCRIPT; do
	echo "Executing $SCRIPT $1"
	eval $SCRIPT $1
done

echo "All found 'CI_hooks_*.sh $1' executed successfully"
