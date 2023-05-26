#!/bin/bash

clear
# show commands as they are run.
set -x

# ===================================================================
# Starts the Quanta server at: http://${quanta_domain}:${PORT}
#
# The only prerequisite software to be installed before running this is
# docker and docker-compose
#
# Docker References: https://docs.docker.com/compose/install/
#
# ===================================================================

source ./setenv-run-distro.sh

./stop-distro.sh

# take ownership of this directory as current user
sudo chown -R $USER .

mkdir -p ./dumps
mkdir -p ./tmp
mkdir -p ./log
mkdir -p ./config
mkdir -p ./data

# Use this to troubeshoot the variable substitutions in the yaml file, and will
# display a copy of the yaml file after all environment variables have been substituted/evaluated
# WARNING: This will expose your passwords in the output file!
# docker-compose -f ${dc_yaml} config --no-cache > final-${dc_yaml}
docker-compose -f ${dc_yaml} config > final-${dc_yaml}

genMongoConfig

# If we detect that the springboot fat jar (the executable) exists in this folder then we run
# the dockerBuild function which does a docker-compose 'build' to update to a new docker image that contains
# this current latest jar file.
if [ -f "${JAR_FILE}" ]; then
    echo "Building new Docker Image (${DOCKER_IMAGE}) based on JAR file: ${JAR_FILE}"
    dockerBuild
else 
    echo "Running from IMAGE: ${DOCKER_IMAGE}"
fi
dockerUp

serviceCheck ${docker_stack}_quanta-distro
serviceCheck ${docker_stack}_mongo-distro

# tserver-tag
serviceCheck ${docker_stack}_tserver-distro

if [[ -z ${ipfsEnabled} ]]; then  
    echo "ipfs not in use"
else
    serviceCheck ${docker_stack}_ipfs-distro
fi

printUrlsMessage
