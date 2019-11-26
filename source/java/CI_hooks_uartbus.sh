cd "$(dirname `readlink -f "$0"`)"

cd uartbus

case $1 in
	clean)
		mvn clean
	;;

	prepare_build)
	;;

	build)
		mvn compile
	;;

	test)
		mvn test
	;;

	deploy)
	;;

	install)
	;;

	*)	# unknown option
		exit 0
	;;
esac
