#!/bin/sh
# Script to install the DirectoryBookmarks application.
# The python3 is required.

set -e

appName=DirectoryBookmarks
sourceUrl=https://raw.githubusercontent.com/pponec/PPScriptsForJava/main/src/main/java/net/ponec/script/python/$appName.py

python3 --version
wget -O $appName.py $sourceUrl
python3 $appName.py i >> ~/.bashrc

echo "The application $appName has been successfully installed to the directory: $PWD"
echo "Reload a configuration by the next statement: . ~/.bashrc"
