#!/bin/bash
# set -x

# force current dir to be this script
script_file=$(realpath $0)
script_folder="$(dirname "${script_file}")"
cd ${script_folder}

source ./setenv-dev.sh

cd ${PRJROOT}
dockerDown ${docker_compose_yaml} quanta-dev
dockerDown ${docker_compose_mongo_yaml} mongo-dev
dockerDown ${docker_compose_ipfs_yaml} ipfs-dev

# docker ps
sleep 3
