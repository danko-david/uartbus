#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

set -e

case $1 in
	clean)
	;;

	prepare_build)
		./scripts/get_c_deps.sh
	;;

	build)
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
