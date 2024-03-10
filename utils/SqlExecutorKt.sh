#!/bin/bash
# Sample SQL scripting for the Bash and Java v17.
# How to install Kotlin for Linux: sudo snap install --classic kotlin

set -e
ktFile="SqlExecutorKt.kt"
ktsFile="${ktFile}s"

cp "$ktFile" "$ktsFile"
sed -i '0,/^\/\/KTS\/\//s// /' "$ktsFile"
kotlin -cp ../lib/h2-2.2.224.jar "$ktsFile" "$@"
rm "$ktsFile"