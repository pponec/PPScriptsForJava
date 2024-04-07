#!/bin/bash
# Sample SQL scripting for the Bash and Java v17.

set -e
cd $(dirname $0)
h2Version=2.2.224
driver=$HOME/.m2/repository/com/h2database/h2/$h2Version/h2-$h2Version.jar
mvn=../../../../../../mvnw.sh

if [ ! -e "$driver" ]; then
   sh $mvn dependency:get -Dartifact=com.h2database:h2:$h2Version
fi

java -cp $driver SqlExecutor.java "$@"
