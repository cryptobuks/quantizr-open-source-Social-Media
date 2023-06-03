#!/bin/bash -i

# WARNING: This script is normally called FROM ./scripts/build.sh where some environment setup
# and other important precitions MUST be met before running this script

if [ -z "$quanta_domain" ]
then
    read -p "\$quanta_domain is empty. Don't run this batch file directly. It's run from a 'build-*.sh' file"
    exit
fi

rm -rf lib
node build.js
verifySuccess "build.js: quanta-common"
# run TSC to emit types now. (important!)
tsc
verifySuccess "tsc: quanta-common"
