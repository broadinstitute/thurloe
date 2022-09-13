#!/bin/bash

set -e

THURLOE_DIR=$1
cd $THURLOE_DIR
sbt clean test assembly
THURLOE_JAR=$(find target | grep 'thurloe.*\.jar')
mv $THURLOE_JAR .
sbt clean
