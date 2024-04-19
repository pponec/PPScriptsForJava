#!/bin/sh
# Script to install the DirectoryBookmarks application.
# Java version 17+ is required.

set -e
javax() {
  if [ -n "$JAVA_HOME" ]
  then "$JAVA_HOME/bin/java" "$@"
  else java "$@"
  fi
}

appName=DirectoryBookmarks
sourceUrl=https://raw.githubusercontent.com/pponec/PPScriptsForJava/main/src/main/java/net/ponec/script/$appName.java

javax --version
wget -O $appName.java $sourceUrl
$javax $appName.java c
$javax -jar $appName.jar i >> ~/.bashrc

echo "The application $appName has been successfully installed to the directory: $PWD"
echo "Reload a configuration by the next statement: . ~/.bashrc"




