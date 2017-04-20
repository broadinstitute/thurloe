#!/bin/bash

# Single source of truth for building Thurloe.
# @ Jackie Roberti
#
# Provide command line options to do one or several things:
#   jar : build thurloe jar
#   -d | --docker : provide arg either "build" or "push", to build and push docker image
# Jenkins build job should run with all options, for example,
#   ./docker/build.sh jar -d push

function make_jar()
{
	echo "building thurloe jar..."
	docker run --rm -v $PWD:/working -v jar-cache:/root/.ivy -v jar-cache:/root/.ivy2 broadinstitute/scala-baseimage /working/docker/install.sh /working
}

function docker_cmd()
{
    DOCKER_CMD=$1
    REPO=broadinstitute/$PROJECT
    if [ $DOCKER_CMD="build" ] || [ $DOCKER_CMD="push" ]; then
        echo "building docker image..."
        GIT_SHA=$(git rev-parse ${GIT_BRANCH})
        echo GIT_SHA=$GIT_SHA > env.properties  # for jenkins jobs
        docker build -t $REPO:${GIT_SHA:0:12} .

        if [ $DOCKER_CMD="push" ]; then
            echo "pushing docker image..."
            docker push $REPO:${GIT_SHA:0:12}
        fi
    else
        echo "Not a valid docker option!  Choose either build or push (which includes build)"
    fi
}

# parse command line options
PROJECT=${PROJECT:-thurloe}
GIT_BRANCH=${GIT_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}  # default to current branch
while [ "$1" != "" ]; do
    case $1 in
        jar) make_jar ;;
        -d | --docker) shift
                       docker_cmd $1
                       ;;
    esac
    shift
done
