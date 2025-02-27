#!/bin/bash

echo "Defining Functions..."
set +a # makes all functions get exported

verifySuccess () {
    if [ $? -eq 0 ]; then
        echo "$1 successful."
    else
        echo "$1 failed. EXIT CODE: $?"
        read -p "Press ENTER."
        exit $?
    fi
}

serviceCheck () {
    if docker service inspect $1 | grep $1; then
        echo "service $1 started ok"
    else
        # if this fails can that mean you just didn't give docker enough time? So maybe it was in process of starting?
        read -p "service $1 failed to start"
    fi
}

imageCheck () {
    if docker image ls | grep $1; then
        echo "image $1 exists"
    else
        # if this fails can that mean you just didn't give docker enough time? So maybe it was in process of starting?
        read -p "image $1 does not exist"
    fi
}

# If you get the buld error: failed to solve: failed to walk: resolve : lstat /var/lib/docker/overlay2/diff: no such file or directory
# try running this docker-comkpose with "--no-cache" option like this:
#     docker-compose -f ${dc_yaml} build --no-cache
dockerBuild () {
    echo "dockerBuild: app"
    # docker-compose -f ${dc_yaml} build --no-cache
    docker-compose -f ${dc_yaml} build
    verifySuccess "Docker Compose: build app"
}

dockerUp() {
    echo "Deploying stack"
    docker stack deploy -c ${dc_yaml} ${docker_stack}
    verifySuccess "Stack deployed."

    echo "waiting ${DOCKER_UP_DELAY}, after deploying..."
    sleep ${DOCKER_UP_DELAY}
}

dockerDown() {
    # Trying to help docker not blow up (which it has been doing), by giving it as graceful a shutdown as I can
    if [[ -z ${ipfsEnabled} ]]; then
        echo "ipfs not enabled"
    else
        # This not yet tested on PROD: I had added this to try to see if it's the reason IPFS cannot stay up (but always 
        # crashes apparently completely on it's own), but I discovered the IPFS lock file failure is not related to shutdown
        # but IS indeed just IPFS deciding to shutdown all on it's own, so for now I'm disabling IPFS completely on prod
        # until I have time to investigate.
        echo "running IPFS internal damon shutdown: Service=${docker_stack}_${ipfs_container}"
        docker exec $(docker ps -q -f name=${docker_stack}_${ipfs_container}) ipfs shutdown ; sleep 3s
        echo "waiting ${DOCKER_DOWN_DELAY} after IPFS shutdown..."
    fi

    echo "Stopping docker stack"
    docker stack rm ${docker_stack}
    echo "waiting ${DOCKER_DOWN_DELAY} after stack removed..."
    sleep ${DOCKER_DOWN_DELAY}
}

printUrlsMessage() {
    echo ================================================
    echo Quanta is Running at: http://${quanta_domain}:${HOST_PORT}
    # echo To Test: curl -X POST  http://${quanta_domain}:${HOST_PORT}/api/ping -H "Accept: application/json" -H "Content-Type: application/json" -d "{}"
    echo ================================================
    read -p "Press enter key."
}

# tip: the "<<" operator below is called a "here document" in Linux terminology.
genMongoConfig() {
    echo "Generating MongoDB Config: ${MONGOD_CONF}"
cat > ${MONGOD_CONF} <<- EOM
# NOTE: This file is generated by the builder.
net:
    port: ${MONGO_PORT}
    bindIp: ${MONGO_HOST}

security:
    authorization: enabled
EOM
}

set -a
echo "Functions ready"

