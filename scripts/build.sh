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
# WARNING: This pom.xml (in common folder) and is SEPARATE and just a way
# to simplify the POMs by separately installing all the common stuff
# from this common pom. Both POMS are necessary!
echo "mvn install the /pom/common/pom.xml into repo"
mvn -T 1C install -Dmaven.javadoc.skip=true
verifySuccess "Maven install common pom"

cd ${PRJROOT}

# This build command creates the SpringBoot fat jar in the /target/ folder.
echo "Maven CLEAN package ${mvn_profile}"
# This run is required only to ensure TypeScript generated files are up to date.
# Always do the same profile here (dev-vscode)
mvn -T 1C package -DskipTests=true -Pdev-vscode
verifySuccess "Maven install commmon dev-vscode (typescript gen)"

# These aren't normally needed, so I'll just keep commented out most of time. Tip: Only run any of these AFTER
# you've successfully run a build, and if you changed a bunch of stuff delete ".m2" folder on your machine first!
# mvn dependency:sources clean
# mvn dependency:resolve -Dclassifier=javadoc
# mvn dependency:tree clean exec:exec package -DskipTests=true -Dverbose

cp src/main/resources/public/ts/JavaIntf.ts src/main/resources/quanta-common/JavaIntf.ts

cd ${PRJROOT}/src/main/resources/quanta-common
. ./build.sh

cd ${PRJROOT}/src/main/resources/public
. ./build.sh

cd ${PRJROOT}/src/main/resources/server
. ./build.sh

cd ${PRJROOT}
# Then this is the actual maven full build of the server springboot app
mvn -T 1C clean package -DskipTests=true -P${mvn_profile}
verifySuccess "Maven Build"
