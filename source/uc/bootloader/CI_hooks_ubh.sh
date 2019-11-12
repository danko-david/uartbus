#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

case $1 in
	clean)
		rm *.{hex,sizes,map,asm,o,out} || true
	;;

	prepare_build)
	;;

	build)
		./compile_and_upload.sh atmega328p 115200 4 -1
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