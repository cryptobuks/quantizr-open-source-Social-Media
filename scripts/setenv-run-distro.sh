#!/bin/bash

source ./define-functions.sh

export QUANTA_VER=2.14
export DOCKER_TAG=quanta-${QUANTA_VER}

# Uncomment either option 1 or option 2
# ================================================================
# OPTION 1) To pull from Docker Hub
#
# export DOCKER_IMAGE=subnode/repo:quanta-${QUANTA_VER}
# export TSERVER_IMAGE=subnode/repo:tserver-${QUANTA_VER}
# export JAR_FILE=
# ----------------------------------------------------------------
# OPTION 2) To Build Image from a local JAR
#
#    Having this JAR_FILE exist in the current folder will trigger a full docker-compose build
#    and install that JAR into the DOCKER_IMAGE specified here. After this you can delete the JAR
#    and it will be able to run from the local repo cache, or drop in an updated jar any time
#    and run again to once again put the jar into the repo and upgrade to that latest jar.
export DOCKER_IMAGE=quanta-${QUANTA_VER}

# tserver-tag
export TSERVER_IMAGE=tserver-${QUANTA_VER}
export JAR_FILE=./quanta-0.0.1-SNAPSHOT.jar
# ================================================================

export DEPLOY_TARGET=$PWD

export DOCKER_NETWORK=bridge

# If you're using a DNS name that should go here instead of the ip.
# This is the domain name as your BROWSER sees it.
# The 172.17.0.1 value is the default gateway docker creates for it's 'bridge' network, which I *think* a constant.
#  but can be verified by running `docker network inspect bridge`.
export quanta_domain=172.17.0.1

export dc_yaml=dc-distro.yaml
export docker_stack=quanta-stack-distro

export ipfs_data=${DEPLOY_TARGET}/ipfs
export ipfs_staging=${DEPLOY_TARGET}/ipfs/staging

# make this BLANK for disabled, and "true" for enabled. When enabling don't forget to add the
# dependency in the dockercompose YAML file to start IPFS deamon before the app starts
export ipfsEnabled=
export ipfs_container=ipfs-distro

export HOST_PORT=80
export PORT=80
export PORT_DEBUG=8000
export XMS=1000m
export XMX=2500m

export MONGO_DATA=${DEPLOY_TARGET}/data
export MONGOD_CONF=${DEPLOY_TARGET}/mongod.conf
export MONGO_HOST=mongo-host-distro
export MONGO_PORT=27020

# tserver-tag
export TSERVER_PORT=4002

export DOCKER_DOWN_DELAY=15s
export DOCKER_UP_DELAY=20s

# SECRETS 

# Fill these in if you are supporting signups which requires you to have access
# to an email server, but won't be required if you're running your peer as a single
# user instance, or just doing localhost p2p testing/development.
export emailPassword=

# Warning: To be able to create our test accounts we need this email prop defined even
# even if it's a dummy string
export devEmail=somebody@someserver.com

# admin password: login to web app with "admin/password" credentials. Note also that
# this password is used in the yaml as the root password for MongoDB.
export adminPassword=password
export mongoPassword=password

# This is the password that will be used by the auto-generated test accounts you'll see 
# in the docker yaml for accounts adam, bob, cory, etc.
export testPassword=password

# OVERRIDE SECRETS IN here....

# If this additional variable setter file exists we run it, so it can 
# be used to override any of these settings

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
