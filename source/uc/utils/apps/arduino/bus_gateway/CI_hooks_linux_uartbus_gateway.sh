cd "$(dirname `readlink -f "$0"`)"

set -e

case $1 in
	clean)
		rm *.{d,o,hex,asm,a} || true
	;;

	prepare_build)
	;;

	build)
		./target_atmega2560_make_and_upload.sh 115200 -1
		./target_atmega328_make_and_upload.sh 115200 -1
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
