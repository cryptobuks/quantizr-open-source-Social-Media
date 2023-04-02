#!/bin/bash

echo "Generating SCSS Imports"

# GENERATE imports.scss file
# (Imports files RELATIVE to 'css' folder by being in css folder for this command.)
cd ${PRJROOT}/src/main/resources/public/css
echo "// WARNING: Auto-generated File (see build.sh)" > imports.scss
printf "@import '%s';\n" ./imports/*.scss >> imports.scss
printf "@import '%s';\n" ../ts/**/*.scss >> imports.scss

# NOTE: Apparently there's a bug in printf command because it doesn't seem like I should need
# to supply more than one "**" a directory to get full recursion but it's not working unless I put
# all these deeper wildcards
printf "@import '%s';\n" ../ts/**/**/*.scss >> imports.scss

