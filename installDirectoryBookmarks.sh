#!/bin/sh
# Script to install the DirectoryBookmarks application.
# Java version 17+ is required.

set -e
javaExe=$JAVA_HOME/bin/java
appName=DirectoryBookmarks

$javaExe --version
wget -O $appName.java https://raw.githubusercontent.com/pponec/$appName/main/$appName.java
$javaExe $appName.java c
$javaExe -jar $appName.jar i >> ~/.bashrc

echo "The application $appName has been successfully installed to the current directory: $PWD"
echo "Reload a configuration by the next statement: . ~/.bashrc"




