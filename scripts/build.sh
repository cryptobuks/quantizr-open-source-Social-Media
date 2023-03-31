#!/bin/bash

# show commands as they are run.
# set -x

if [ -z "$quanta_domain" ]
then
    read -p "\$quanta_domain is empty. Don't run this batch file directly. It's run from a 'build-*.sh' file"
    exit
fi

echo "Running build.sh for $quanta_domain"

# Wipe some existing stuff to ensure it gets rebuilt
if [ "$CLEAN" == "true" ]; then
    rm -rf ${PRJROOT}/target/*
    rm -rf ${PRJROOT}/bin/*
fi

# copy the marked js file into location where export engine finds it
# The latest marked version that I need for support in static html files is missing
# the typescript type file, so I'm leving my NPM at an older version and putting
# the latest version manually into 'export-includes'
# cp ${PRJROOT}/src/main/resources/public/node_modules/marked/marked.min.js \
#    ${PRJROOT}/src/main/resources/public/export-includes/marked.min.js

# Run ignore-scripts for some security from NodeJS
# Packages can run "postinstall" script from their package.json and that is an attack vector we want to eliminate here.
cd ${PRJROOT}/src/main/resources/public
# NOTE: run 'npm outdated' in this folder to view all outdated versions.
npm config set ignore-scripts true

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
# Note: If CLEAN is false we're going for the fastest possible build.
if [ "$CLEAN" == "true" ]; then
    echo "Maven CLEAN package ${mvn_profile}"
    # This run is required only to ensure TypeScript generated files are up to date.
    # Always do the same profile here (pdev-vscode)
    mvn -T 1C package -DskipTests=true -Pdev-vscode

    # Then this is the actual full build.
    mvn -T 1C clean package -DskipTests=true -P${mvn_profile}
else
    echo "Maven (unclean) package ${mvn_profile}"
    mvn -T 1C --offline package -DskipTests=true -P${mvn_profile} 
fi
verifySuccess "Maven Build"
