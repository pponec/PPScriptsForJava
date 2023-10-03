#!/bin/sh
# Script to install the DirectoryBookmarks application.
# Java version 17+ is required.

set -e
getJavaExe() {
  if [ -n "$JAVA_HOME" ]; then echo "$JAVA_HOME/bin/java"
  else echo "$(which java)"
  fi
}

appName=DirectoryBookmarks
sourceUrl=https://raw.githubusercontent.com/pponec/$appName/main/$appName.java
javaExe=$(getJavaExe)

$javaExe --version
wget -O $appName.java $sourceUrl
$javaExe $appName.java c
$javaExe -jar $appName.jar i >> ~/.bashrc

echo "The application $appName has been successfully installed to the current directory: $PWD"
echo "Reload a configuration by the next statement: . ~/.bashrc"




