#!/bin/bash
# Sample script to parse a JSON

set -e
cd $(dirname "$0")
version=$(java PPUtils.java json version package.json)
echo "Version: $version"