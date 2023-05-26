#!/bin/bash
source ./define-functions.sh

# we make up any prod version we want here. It won't matter. This image is built on and private to the prod instance
# and ultimtely determined by whatever JAR is present plus all the config settings.
export QUANTA_VER=2.14
export DOCKER_TAG=quanta-${QUANTA_VER}
export DOCKER_IMAGE=quanta-${QUANTA_VER}

# tserver-tag
export TSERVER_IMAGE=tserver-${QUANTA_VER}
export JAR_FILE=./quanta-0.0.1-SNAPSHOT.jar

export DEPLOY_TARGET=$PWD

export DOCKER_NETWORK=bridge
export DATA_FOLDER=/home/clay/q2-data

# If you're using a DNS name that should go here instead of the ip.
# This is the domain name as your BROWSER sees it.
export quanta_domain=quanta.wiki

# I'm expanding scope of setenv to have the responsibility of ALSO creating folders
mkdir -pv ${DATA_FOLDER}/docs
mkdir -pv ${DATA_FOLDER}/tmp
mkdir -pv ${DATA_FOLDER}/log
mkdir -pv ${DATA_FOLDER}/ipfs
mkdir -pv ${DEPLOY_TARGET}/config

export dc_yaml=dc-distro.yaml
export docker_stack=quanta-stack-distro

export ipfs_data=${DATA_FOLDER}/ipfs
export ipfs_staging=${DATA_FOLDER}/ipfs/staging

# make this BLANK for disabled, and "true" for enabled. When enabling don't forget to add the
# dependency in the dockercompose YAML file to start IPFS deamon before the app starts
export ipfsEnabled=
export ipfs_container=ipfs-distro

export HOST_PORT=80
export PORT=80

export HOST_PORT_SEC=443
export PORT_SEC=443

export PORT_DEBUG=8000
export XMS=2g
export XMX=4g

export MONGO_DATA=/var/lib/mongodb
export MONGOD_CONF=${DEPLOY_TARGET}/mongod.conf
export MONGO_HOST=mongo-host-distro
export MONGO_PORT=27017

# tserver-tag
export TSERVER_PORT=4000

export DOCKER_DOWN_DELAY=30s
export DOCKER_UP_DELAY=20s

# SECRETS come out of the setenv-quanta-ext.sh (real passwords, etc)

# first apply any overrides that exist in this folder
if [ -f "setenv-quanta-ext.sh" ]; then
    echo "Overriding secrets with setenv-quanta-ext.sh"
    source setenv-quanta-ext.sh
fi

# then apply any overrides from parent folder
if [ -f "../setenv-quanta-ext.sh" ]; then
    echo "Overriding secrets with ../setenv-quanta-ext.sh"
    source ../setenv-quanta-ext.sh
fi
