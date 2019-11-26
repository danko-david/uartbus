#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

set -e

case $1 in
	clean)
		rm *.{hex,sizes,map,asm,o,out} test || true
	;;

	prepare_build)
	;;

	build)
		../../../../../scripts/compile_ub_app.sh atmega328p 16000000 test test.c
	;;

	test)
	;;

	deploy)
	;;

	install)
	;;

	*)	# unknown option
		exit 0
	;;
esac
