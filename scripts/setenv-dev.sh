#!/bin/bash

THIS_FILE=$(readlink -f "$0")
THIS_FOLDER=$(dirname "$THIS_FILE")
export PRJROOT=$(dirname "$THIS_FOLDER")
export PRJPARENT=$(dirname "$PRJROOT")

# Defines some reusable functions that are common to many of these scripts
source ./define-functions.sh

# Define some functions that are specific only to managing the DEV environment
source ./define-functions-dev.sh

export QUANTA_VER=2.14
export DOCKER_IMAGE=quanta-dev-${QUANTA_VER}

# tserver-tag
export TSERVER_IMAGE=tserver-dev-${QUANTA_VER}
export DOCKER_TAG=quanta-dev-${QUANTA_VER}

# Must be the folder where the Quantizr project is located. The root of the source folders.
export SCRIPTS=${PRJROOT}/scripts

# Tells our scripts where the actual executable code is expected to be found
export JAR_FILE=target/quanta-0.0.1-SNAPSHOT.jar

export DOCKER_NETWORK=bridge

# If you're using a DNS name that should go here instead of the ip.
# This is the domain name as your BROWSER sees it.
# The 172.17.0.1 value is the default gateway docker creates for it's 'bridge' network, which I *think* a constant.
#  but can be verified by running `docker network inspect bridge`.
export quanta_domain=172.17.0.1

# IMPORTANT: ***** You must set this to 'true' to regenerate the Java->TypeScript interfaces.
export CLEAN=true

# Docker files are relative to project root. We hold these in variables so that none of the scripts have them hardcoded
export dc_yaml=dc-dev.yaml
export docker_stack=quanta-stack-dev

# Configure some locations for IPFS-related runtime files
export ipfs_data=/home/clay/.ipfs
export ipfs_staging=/home/clay/.ipfs/staging

# make this BLANK for disabled, and "true" for enabled. When enabling don't forget to add the
# dependency in the dockercompose YAML file to start IPFS deamon before the app starts
export ipfsEnabled=
export ipfs_container=ipfs-dev

# When we run Maven builder, this selects our profile.
export mvn_profile=dev

# Configue the application ports
export HOST_PORT=8182
export PORT=8182
export PORT_DEBUG=8000

# Configure memory allocations
export XMS=512m
export XMX=2g

# Configure MongoDB-specific variables
export MONGO_HOST=mongo-host-dev
export MONGO_PORT=27016

# tserver-tag
export TSERVER_PORT=4002

# NOTE: This file gets *generated* by the build.
export MONGOD_CONF=${PRJROOT}/mongod-dev.conf

# Sets a base location for MongoDB
export MONGO_BASE=${PRJPARENT}

# This tells our scripts where we are actually running from 
# (The Distro Folder on this machine. The folder containing the runtime and configs)
export QUANTA_BASE=/home/clay/quanta-localhost-dev

export DOCKER_DOWN_DELAY=5s
export DOCKER_UP_DELAY=5s

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
if [ -f "${PRJPARENT}/secrets/setenv-quanta-ext.sh" ]; then
    echo "Overriding Secrets with: ${PRJPARENT}/secrets/setenv-quanta-ext.sh"
    source ${PRJPARENT}/secrets/setenv-quanta-ext.sh
else 
    echo "Environment Override didn't exist: ${PRJPARENT}/secrets/setenv-quanta-ext.sh"
    read -p "Press ENTER to run with default secrets."
fi
