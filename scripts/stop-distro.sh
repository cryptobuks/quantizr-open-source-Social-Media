#!/bin/bash

source ./setenv-run-distro.sh

# echo "Stopping any existing server instance..."
# curl http://${quanta_domain}:${PORT}/api/shutdown?password=${adminPassword}

dockerDown
