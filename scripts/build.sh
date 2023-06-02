#!/bin/bash -i

# show commands as they are run.
# set -x

if [ -z "$quanta_domain" ]
then
    read -p "\$quanta_domain is empty. Don't run this batch file directly. It's run from a 'build-*.sh' file"
    exit
fi

echo "Running build.sh for $quanta_domain"

rm -rf ${PRJROOT}/target/*
rm -rf ${PRJROOT}/bin/*

# copy the marked js file into location where export engine finds it
# The latest marked version that I need for support in static html files is missing
# the typescript type file, so I'm leving my NPM at an older version and putting
# the latest version manually into 'export-includes'
# cp ${PRJROOT}/src/main/resources/public/node_modules/marked/marked.min.js \
#    ${PRJROOT}/src/main/resources/public/export-includes/marked.min.js

cd ${PRJROOT}/pom/common

# build with apidocs
# mvn install javadoc:javadoc 

# build without apidocs
# WARNING: This pom.xml is in common and is SEPARATE and just a way
# to simplify the POMs by separately installing all the common stuff
# from this common pom. Both POMS are necessary!
echo "mvn install the /pom/common/pom.xml into repo"
mvn -T 1C install -Dmaven.javadoc.skip=true

cd ${PRJROOT}
./scripts/gen-css-imports.sh

cd ${PRJROOT}
# These aren't normally needed, so I'll just keep commented out most of time. 
# mvn dependency:sources
# mvn dependency:resolve -Dclassifier=javadoc
# mvn dependency:tree clean exec:exec package -DskipTests=true -Dverbose

# This build command creates the SpringBoot fat jar in the /target/ folder.
echo "Maven CLEAN package ${mvn_profile}"
# This run is required only to ensure TypeScript generated files are up to date.
# Always do the same profile here (dev-vscode)
mvn -T 1C package -DskipTests=true -Pdev-vscode

cp src/main/resources/public/ts/JavaIntf.ts src/main/resources/quanta-common/JavaIntf.ts

# --------------------------------------------------------
cd ${PRJROOT}/src/main/resources/quanta-common
rm -rf lib
yarn
verifySuccess "yarn: quanta-common"
node build.js
verifySuccess "build.js: quanta-common"
# run TSC to emit types now. (important!)
tsc
verifySuccess "tsc: quanta-common"

# --------------------------------------------------------
# Web App (not Yarn based, NPM)
# --------------------------------------------------------
# Run ignore-scripts for some security from NodeJS
# Packages can run "postinstall" script from their package.json and that is an attack vector we want to eliminate here.
cd ${PRJROOT}/src/main/resources/public
# NOTE: run 'npm outdated' in this folder to view all outdated versions.
npm config set ignore-scripts true
verifySuccess "NPM Config set for: public"
# npm i
npm uninstall ../quanta-common
npm install --install-links ../quanta-common
verifySuccess "NPM package install: public"

# --------------------------------------------------------
cd ${PRJROOT}/src/main/resources/server
rm -rf ./build
# NOTE: run 'npm outdated' in this folder to view all outdated versions.
# yarn remove quanta-common
yarn add ../quanta-common
verifySuccess "Yarn (server): add quanta-common"
yarn
verifySuccess "Yarn Install (server)"
yarn run build
verifySuccess "NPM run build: server"
# --------------------------------------------------------

cd ${PRJROOT}
# Then this is the actual full build.
mvn -T 1C clean package -DskipTests=true -P${mvn_profile}
verifySuccess "Maven Build"
