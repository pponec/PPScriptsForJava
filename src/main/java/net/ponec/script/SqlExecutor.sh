#!/bin/bash
# Sample SQL scripting for the Bash and Java v17.

set -e
cd $(dirname $0)
version=2.2.224
driver=$HOME/.m2/repository/com/h2database/h2/$version/h2-$version.jar
mvn=../../../../../../mvnw.sh

if [ ! -e "$driver" ]; then
   sh $mvn dependency:get -Dartifact=com.h2database:h2:$version
fi

java -cp $driver SqlExecutor.java "$@"
