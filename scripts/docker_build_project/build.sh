#!/bin/bash

cd "$(dirname `readlink -f "$0"`)"

cd ../../
pwd
docker build -f ./scripts/docker_build_project/Dockerfile .
