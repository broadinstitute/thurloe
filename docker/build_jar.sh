#!/bin/bash

# This script provides an entry point to assemble the Thurloe jar file.

# Enable strict evaluation semantics
set -e


echo "building thurloe jar..."

docker run --rm -v $PWD:/working \
-v jar-cache:/root/.ivy \
-v jar-cache:/root/.ivy2 sbtscala/scala-sbt:openjdk-17.0.2_1.7.2_2.13.10 /working/docker/install.sh /working


EXIT_CODE=$?

if [ $EXIT_CODE != 0 ]; then
    echo "jar build exited with status $EXIT_CODE"
    exit $EXIT_CODE
fi
