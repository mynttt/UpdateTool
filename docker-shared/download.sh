#!/bin/bash

VERSION_URL=https://raw.githubusercontent.com/mynttt/UpdateTool/master/VERSION
wget --no-cache -O VERSION "$VERSION_URL" 2>&1 | grep "^wget:"
VERSION=$(cat "VERSION")
echo "**** Downloading version ${VERSION} ****"
__DURL="https://github.com/mynttt/UpdateTool/releases/download/${VERSION}/UpdateTool-${VERSION}.jar"
wget -O tool.jar "$__DURL" || exit 1
