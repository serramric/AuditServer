#!/bin/bash

set -e

./gradlew build -x test
export VERSION=$(grep "version =" build.gradle | awk '{print $3}' | sed "s/'//g")
./gradlew createDockerfile
./gradlew buildImage -x test

