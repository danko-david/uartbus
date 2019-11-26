#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

cd ../WD/

if [ -d "toe" ]; then
	cd toe
	git pull origin master
	git reset --hard
	cd ..
else
	git clone https://github.com/danko-david/toe.git
fi;


if [ -d "lazyctest" ]; then
	cd lazyctest
	git pull origin master
	git reset --hard
	cd ..
else
	git clone https://github.com/danko-david/lazyctest.git
fi;


