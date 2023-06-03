# How to Run/Build Quanta

## Overview

The easiest way to run Quanta is to run the Public Docker Repo image, without building the executables yourself. This file will explain how to do that as well as how to build the executable yourself, using Linux shell scripts. Regardless of whether you run from the Docker public repository image, or a docker image you build yourself, you'll need a runtime folder that holds all the configuration files for the installation. You can crete this runtime folder by unzipping the "Distro Zip" which contains all that stuff, pre-configured with usable defaults. Inside that zip file is also the `run-distro.sh` which will run the app.

## Option #1: Run from Public Docker Image

To run a Quanta instance from the Docker Public Repository you can simply unzip the Distro Zip file (named like `./distro/quanta-1.0.25.tar.gz`) onto an empty folder, and then start the app with `run-distro.sh`. To stop the app run `stop-distro.sh`. The only prerequisite for this is that you have `docker` and `docker-compose` installed, and you need to setup docker to have 'swarm' capability using this command: `docker swarm init`

NOTE: You should edit the password in the `setenv-*.sh` file before your first run, because that password will become your `admin` user password which you can use to login as the `admin` user in the app, and also will be the password securing your MongoDB instance. Also before you run the app you should probably at least look in the `setenv-*.sh` file, to see what configs are in there, and perhaps the docker-compose YAML file too, but they should all contain usable defaults right out of the box.

So to reiterate, this Distro Zip contains everything needed to configure the app, and by default it will have the effect of using the Public Docker image as the executable to run. Below, "Option #2" describes how to run your own executable that you build yourself.

## Option #2: Run from Locally-built Executable 

If you want to run Quanta executable code you've built yourself, you'll still use a Distro Zip to create a configuration location and files (by just unzipping the Distro Zip into some folder), but after unzipping this Distro Zip you'll drop in the actual SpringBoot fat-jar file (that you built) onto the Distro Zip directory before running `run-distro.sh`. The scripts in the distro folder will automatically detect that the SpringBoot file exists (by looking for the `${JAR_FILE}` file named in the `setenv*.sh`), and if that JAR file (executable) is found then the script will automatically do a docker build that installs and runs from this local JAR file instead of the Public Docker Repository file.

So to summarize, your Distro Zip files will automatically use the JAR file if it's found, or default to the Public Docker Repo if no JAR is found.

## Building the Distro Zip (and executable JAR)

If the above made sense so far, you'll know there's a zip file (the Distro Zip) which contains a confuration for the installation files, and also a SpringBoot fat JAR that can be built too. Both of those things (Distro Zip file, and Fat JAR file) can themselves be built from scratch using `./scripts/build-distro.sh`, which will build them both at the same time. After you run the `build-distro.sh` you'll find the Fat JAR in the `./target/` folder and the Distro Zip file in the `./distro` folder. 

To repeat again for clarity: You can simply unzip the Distro Zip file into some empty folder, then drop the Fat JAR into that folder, and then run script `run-distro.sh` to get a server up and running.

## How to Develop Quanta

This section gives the recommended way for working on Quanta, as a developer, working on making code changes. That is, how to edit code, rebuild, and then test your changes, as rapidly as possible, including techniques that allow you to rapidly edit either Java or TypeScript code and see the results (test the code) almost immediately without waiting for a full build.

 Basically this involves doing something very similar to the above process to first create a runtime installation on your computer (separate and away from the GitHub source folder) where you will create the Distro files, and be able to run from there. Then you will adapt and use the files named `build-dev.sh` (which you won't necessarily edit) and `setenv-dev.sh` (which you will definitely edit). Both `build-dev.sh` and `setenv-dev.sh` are complete and do work, but you'll want to put into the setenv file your own port numbers, folder locations, etc. Also when you run `build-dev.sh` you can run it right from where it's located in the `scripts` folder. Don't move it into your Distro folder.
 
 Most of the varible names are all the same in any `setenv*.sh` files and there's the same set of variables for both a prod deploy and a dev deploy. If you look in `setenv-dev.sh` you'll see that each variable setting has a short description telling you what needs to go there and what that varible is doing.

Once you have `setenv-dev.sh` all configured you should be able to run `build-dev.sh` which will build the code from scratch and start an instance of Quanta, that you can test against. If you then edit only TypeScript files and want to test those changes you can run just `build-webpack.sh` to make those changes go live. Or if you edit only Java files you can run `restart-dev.sh` to see those changes go live, and immediately test them. 

## How the "semi" Hot-Deploy Works

As just stated you can test TypeScript and Java file changes without doing a FULL build. It's worth mentioning here the tricks that allow that to work which are bascically done using the following volume definitions in the docker-compose YAML:

```
    - '${PRJROOT}/src/main/resources/public:/dev-resource-base'
    - '${PRJROOT}/target/classes:/loader-path'
```

And then Spring Boot itself takes care of loading from those locations using this properties file setting:

```
spring.resources.static-locations=classpath:/public/,file:///dev-resource-base/
```

The other thing required to make this [almost] Hot Deploy work is that you need the following Maven plugin `ZIP` Layout, to make these CLASS files be able to load directly from the file system:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layout>ZIP</layout>
    </configuration>
</plugin>
```

You'll notice neither of those two volumes are defined for the distro (PROD) YAML, because they're specifically development-time tricks that allow you to override the executables used at runtime, to inject fresh Java CLASS files whenever you build. Because you're injecting these class files directly at the file-system layer, you can simply tell docker to restart the server (which is pretty quick), and make the new executable class files "go live" instantly.

## Troubleshooting 

If something doesn't work, here's some tips to troubleshoot docker and the app:

### Check to see what Docker has Running

    docker ps
    docker network ls
    docker service ls

Should show something like these two: 'subnode/repo:quanta1.0.26' and 'mongo:4.0'

### Check Docker Images

https://docs.docker.com/engine/reference/commandline/image_ls/

    docker image ls --digests | grep "quanta"
    docker image ls --digests | grep "mongo"
    docker image ls --digests | grep "ipfs"

### Inspect your Docker Repository

    docker image inspect subnode/repo:quanta1.0.26

The "RepoDigests" in the output should contain the same digest shown in the "hub.docker.com" website.

### Check Docker Logs

    docker logs quanta-distro

### Try to Pull again. 

Should tell you image is already up to date

    docker image pull subnode/repo:quanta1.0.26

### To Remove Most Running things

    docker kill $(docker ps -q)

    docker stack ls
    docker stack rm ${stack_name}

    docker service ls
    docker service rm ${serice_name}

    docker network ls
    docker network prune

### Prune entire system of unused things

    docker system prune -a

### IPFS Not Working ?

Run this to see the ipfs logs:

    docker logs ipfs

If you see an error like this:

    Error: lock /data/ipfs/repo.lock: permission denied

Then that means something went wrong starting or terminating IPFS, and the emergency fix for that which is hopefully safe seems to work is to just reboot your Linux instance to be sure everything's clean and nothing in IPFS is running, and then simply delete the 'repo.lock' file. This should then allow IPFS to be working fine the next time you startup Quanta. This is a bug in IPFS not a Quanta bug. The IPFS system doesn't always correctly manage this lock file.
