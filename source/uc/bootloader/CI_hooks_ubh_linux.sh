#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

case $1 in
	clean)
		rm ubh_linux || true
	;;

	prepare_build)
	;;

	build)
		./linux_compile.sh
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
