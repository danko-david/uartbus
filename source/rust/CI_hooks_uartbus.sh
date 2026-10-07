#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

cd uartbus

set -e

case $1 in
	clean)
		cargo clean
	;;

	prepare_build)
	;;

	build)
		cargo build --release
	;;

	test)
		cargo test
	;;

	deploy)
	;;

	install)
	;;

	*)	# unknown option
		exit 0
	;;
esac
