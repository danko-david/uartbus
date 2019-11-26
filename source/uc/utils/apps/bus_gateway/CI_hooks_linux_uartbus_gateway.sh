cd "$(dirname `readlink -f "$0"`)"

set -e

case $1 in
	clean)
		rm linux_uartbus_gateway linux_bus_gateway || true
	;;

	prepare_build)
	;;

	build)
		./target_linux_compile.sh
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
