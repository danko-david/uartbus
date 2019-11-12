#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

cd ../WD/

if [ -d "toe" ]; then
	cd toe
	git pull origin master
	git reset --hard
else
	git clone https://github.com/danko-david/toe.git
fi;


