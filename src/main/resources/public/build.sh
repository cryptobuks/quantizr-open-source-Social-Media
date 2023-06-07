#!/bin/bash -i

# WARNING: This script is normally called FROM ./scripts/build.sh where some environment setup
# and other important precitions MUST be met before running this script

if [ -z "$quanta_domain" ]
then
    read -p "\$quanta_domain is empty. Don't run this batch file directly. It's run from a 'build-*.sh' file"
    exit
fi

yarn run ${WEBPACK_SCRIPT}
verifySuccess "yarn webpack run: public"

# Note: quanta.scss specifies this: $fa-font-path: "../fonts/fa"; pointing to this fonts folder.
# The rest of the font awesome config is accomplished simply by including the font-awesome scss 
# folder (in node_modules) into our SCSS main file (quanta.css).
# See 
rsync -aAX --delete --force "./node_modules/font-awesome/fonts/" "./fonts/fa/"
yarn run ${SASS_SCRIPT}
verifySuccess "yarn sass run: public"
