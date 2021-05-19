#!/bin/bash

# Fill these in if you are supporting signups which requires you to have access
# to an email server, but won't be required if you're running your peer as a single
# user instance, or just doing localhost p2p testing/development.
export emailPassword=
export devEmail=

# admin password: login to web app with "admin/password" credentials. Note also that
# this matches the password in mongo.env, and so the two are synced.
export testPassword=password

# This is the password that will be used by the auto-generated test accounts you'll see 
# in the docker yaml for accounts adam, bob, cory, etc.
export subnodePassword=password

