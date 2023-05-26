#!/bin/bash

# *********** Special Hack for Clay Ferguson's machine (everyone else delete this!)
# docker stack rm quanta-stack-distro
# sudo rm -r /home/clay/ferguson/distro-test
# mkdir /home/clay/ferguson/distro-test
# *********************************************************************************

# =================================================================================================
# Builds a production distro zip file, which should be able to be unzipped and run on any
# linux box to run an instance of Quanta.
# =================================================================================================

clear
# show commands as they are run.
# set -x

# Set all environment variables
source ./setenv-build-distro.sh
THIS_FILE=$(readlink -f "$0")
THIS_FOLDER=$(dirname "$THIS_FILE")

sudo rm -r ${DEPLOY_TARGET}

echo "Preparing DEPLOY_TARGET: ${DEPLOY_TARGET}"
mkdir -p ${DEPLOY_TARGET}

# remove this to be sure we will notice if it doesn't successfully build
sudo rm -f ${PRJROOT}/distro/quanta${QUANTA_VER}.tar.gz

# sanity check since we do "rm -rf" in here
if [ -z "$DEPLOY_TARGET" ]; then exit; fi
sudo rm -rf ${DEPLOY_TARGET}/*
verifySuccess "Cleaned deploy target"

mkdir -p ${DEPLOY_TARGET}/log
# Copy our primary logger file out to the live-loadable configured location
# (note: without the 'logging.config' being set in the docker yaml this file would
# load from /src/main/resouces which is the spring default location.)
cp ${PRJROOT}/src/main/resources/logback-spring.xml ${DEPLOY_TARGET}/log/logback.xml

# copy some configs and scripts to deploy target
cd ${PRJROOT}
cp ${PRJROOT}/dc-distro.yaml                    ${DEPLOY_TARGET}
cp ${PRJROOT}/dockerfile                        ${DEPLOY_TARGET}
cp ${PRJROOT}/entrypoint.sh                     ${DEPLOY_TARGET}
cp ${PRJROOT}/distro/README.md                  ${DEPLOY_TARGET}

# tserver-tag
mkdir -p ${DEPLOY_TARGET}/src/main/resources/server
cp -R ${PRJROOT}/src/main/resources/server/ ${DEPLOY_TARGET}/src/main/resources/

# copy scripts needed to start/stop to deploy target
cp ${SCRIPTS}/run-distro.sh                 ${DEPLOY_TARGET}
cp ${SCRIPTS}/stop-distro.sh                ${DEPLOY_TARGET}
cp ${SCRIPTS}/define-functions.sh           ${DEPLOY_TARGET}
cp ${SCRIPTS}/setenv-run-distro.sh          ${DEPLOY_TARGET}

# Note: this 'dumps' folder is mapped onto a volume in 'dc-distro.yaml' and the 'backup-local.sh'
#       script should only be run from 'inside' the docker container, which is what 'mongodb-backup.sh' actually does.
mkdir -p ${DEPLOY_TARGET}/dumps
mkdir -p ${DEPLOY_TARGET}/tmp
mkdir -p ${DEPLOY_TARGET}/log
mkdir -p ${DEPLOY_TARGET}/config
mkdir -p ${MONGO_DATA}

# Default app configs
# We only need this if overriding/extending the default properties
# cp ${PRJROOT}/src/main/resources/config-text-distro.yaml    ${DEPLOY_TARGET}/config

# copy our banding folder to deploy target
rsync -aAX --delete --force --progress --stats "${PRJROOT}/branding/" "${DEPLOY_TARGET}/branding/"

# ensure the IPFS folders exist
mkdir -p ${ipfs_data}
mkdir -p ${ipfs_staging}

# Wipe previous springboot fat jar to ensure it can't be used again.
rm -f ${PRJROOT}/target/quanta-0.0.1-SNAPSHOT.jar

# build the project (compile all source)
cd ${PRJROOT}
. ${SCRIPTS}/build.sh

cp ${PRJROOT}/target/quanta-0.0.1-SNAPSHOT.jar ${DEPLOY_TARGET}
verifySuccess "JAR copied to build distro"

genMongoConfig

# This builds the images locally, and saves them into local docker repository, so that 'docker-compose up',
# is all that's required.
cd ${DEPLOY_TARGET}
dockerBuild
echo "Docker build complete..."

imageCheck ${DOCKER_TAG}

# Now fix up the DEPLOY_TARGET and for end users, and zip it

rm -f ${DEPLOY_TARGET}/quanta-0.0.1-SNAPSHOT.jar

# Copy over the Backup/Restore scripts
cp ${SCRIPTS}/tools/_backup.sh          ${DEPLOY_TARGET}/dumps
cp ${SCRIPTS}/tools/_restore.sh         ${DEPLOY_TARGET}/dumps
cp ${SCRIPTS}/tools/backup.sh           ${DEPLOY_TARGET}
cp ${SCRIPTS}/tools/restore.sh          ${DEPLOY_TARGET}

TARGET_PARENT="$(dirname "${DEPLOY_TARGET}")"
cd ${TARGET_PARENT}

tar -zcvf ${PRJROOT}/distro/quanta${QUANTA_VER}.tar.gz quanta-distro
#NOTE: Extraction command will be: `tar vxf quanta1.0.3.tar.gz`
verifySuccess "TAR create: ${PRJROOT}/distro/quanta${QUANTA_VER}.tar.gz"

# *********** Special Hack for Clay Ferguson's machine (everyone else delete this!)
# cp ${PRJROOT}/distro/quanta${QUANTA_VER}.tar.gz /home/clay/ferguson/distro-test
# cd /home/clay/ferguson/distro-test
# tar -xf quanta${QUANTA_VER}.tar.gz
# cp ${PRJROOT}/target/quanta-0.0.1-SNAPSHOT.jar /home/clay/ferguson/distro-test/quanta-distro
# **********************************************************************************

echo
echo "==================== NOTE ======================================="
echo "Run docker-publish-distro.sh to publish the distro to docker repo"
echo "You can test locally (before publishing) by running:"
echo "${DEPLOY_TARGET}/run-distro.sh"
echo "================================================================="
echo 
echo "************ Build Complete: ${PRJROOT}/distro/quanta${QUANTA_VER}.tar.gz"

