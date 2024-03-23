#!/bin/bash
# Sample SQL scripting for the Bash and Kotlin.
# How to install Kotlin to Ubuntu: sudo snap install --classic kotlin

set -e
cd $(dirname $0)
version=2.2.224
driver=$HOME/.m2/repository/com/h2database/h2/$version/h2-$version.jar
mvn=../../../../../../mvnw.sh
if [ ! -e "$driver" ]; then
   sh $mvn dependency:get -Dartifact=com.h2database:h2:$version
fi

ktFile="SqlExecutorKt.kt"
ktsFile="${ktFile}s"
cp "$ktFile" "$ktsFile"
sed -i '0,/^\/\/KTS\/\//s// /' "$ktsFile"
kotlin -cp $driver "$ktsFile" "$@"
rm "$ktsFile"