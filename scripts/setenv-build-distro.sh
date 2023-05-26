#!/bin/bash
# This is the version of the 'setenv' file that's used to build the distro. The file named
# 'setenv-run-distro.sh' is the environment setter for the runtime/deployment.

source ./define-functions.sh
THIS_FILE=$(readlink -f "$0")
THIS_FOLDER=$(dirname "$THIS_FILE")
export PRJROOT=$(dirname "$THIS_FOLDER")
export PRJPARENT=$(dirname "$PRJROOT")

export QUANTA_VER=2.14

# always leave 'subnode/repo:' here so our publish to Docker Public Hub works, and no other
# builds we run on our machine should ever target to the 'subnode/repo:'
export DOCKER_IMAGE=subnode/repo:quanta-${QUANTA_VER}

# tserver-tag
export TSERVER_IMAGE=subnode/repo:tserver-${QUANTA_VER}
export DOCKER_TAG=quanta-${QUANTA_VER}

export SCRIPTS=${PRJROOT}/scripts
# This jar file will be the one copied into the distro folder. The folder that will be zipped but without the file in it.
export JAR_FILE=./quanta-0.0.1-SNAPSHOT.jar

export DOCKER_NETWORK=bridge

# If you're using a DNS name that should go here instead of the ip.
# This is the domain name as your BROWSER sees it.
# The 172.17.0.1 value is the default gateway docker creates for it's 'bridge' network, which I *think* a constant.
#  but can be verified by running `docker network inspect bridge`.
export quanta_domain=172.17.0.1

# IMPORTANT: ***** You must set this to 'true' to regenerate the Java->TypeScript interfaces.
export CLEAN=true

export dc_yaml=dc-distro.yaml
export docker_stack=quanta-stack-distro

export mvn_profile=prod

# make this BLANK for disabled, and "true" for enabled. When enabling don't forget to add the
# dependency in the dockercompose YAML file to start IPFS deamon before the app starts
export ipfsEnabled=
export ipfs_container=ipfs-distro

# deploy target folder is where we will be running the app from or what will become the ZIP file content
export DEPLOY_TARGET=${PRJPARENT}/quanta-distro
mkdir -p ${DEPLOY_TARGET}

export ipfs_data=${DEPLOY_TARGET}/ipfs
export ipfs_staging=${DEPLOY_TARGET}/ipfs/staging

# Note: define-functions.sh is where we pass the ARGS into dockerfile
export HOST_PORT=80
export PORT=80
export PORT_DEBUG=8000
export XMS=512m
export XMX=2g

export MONGO_DATA=${DEPLOY_TARGET}/data
export MONGO_HOST=mongo-host-distro
export MONGO_PORT=27020
export MONGOD_CONF=${DEPLOY_TARGET}/mongod.conf

# tserver-tag
export TSERVER_PORT=4000

export DOCKER_DOWN_DELAY=15s
export DOCKER_UP_DELAY=15s

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