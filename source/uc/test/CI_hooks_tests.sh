#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

PATH=$PATH:../../../WD/lazyctest/WD/bin

set -e

case $1 in
	clean)
		rm lct_result_*.junit.xml || true
	;;

	prepare_build)
	;;

	build)
		gcc -DUB_TEST -fPIC -shared -g -rdynamic -I../bus/lib/common -I../commons -I../utils/lib/rpc test_uartbus.c -o test_uartbus.so
		gcc -DUB_TEST -DUBH_TEST -fPIC -shared -g -rdynamic -I../bus/lib/common -I../commons -I../utils/lib/rpc ../utils/lib/rpc/rpc.c test_ubh.c -o test_ubh.so
	;;

	test)
		lct ./test_uartbus.so
		lct ./test_ubh.so
	;;

	deploy)
	;;

	install)
	;;

	*)	# unknown option
		exit 0
	;;
esac
