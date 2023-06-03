#!/bin/bash -i

# show commands as they are run.
# set -x
source ./setenv-dev.sh

cd ${PRJROOT}/src/main/resources/public
. ./build.sh
