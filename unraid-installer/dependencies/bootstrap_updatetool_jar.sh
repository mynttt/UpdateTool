#!/bin/bash

set -e

VERSION_URL=https://raw.githubusercontent.com/mynttt/UpdateTool/master/VERSION

if [ -f LOCAL_VERSION ]; then
    wget --no-cache -O TMP_VERSION "$VERSION_URL"
    VERSION=$(cat "TMP_VERSION")
    VERSION_LOCAL=$(cat "LOCAL_VERSION")
    if [ $VERSION != $VERSION_LOCAL ]; then
        __DURL="https://github.com/mynttt/UpdateTool/releases/download/${VERSION}/UpdateTool-${VERSION}.jar"
        (wget -O tool.jar "$__DURL" && echo $VERSION > LOCAL_VERSION) || exit 1
    else
        echo "**** UpdateTool is up to date! Nothing to do. ****"
    fi
else
    wget --no-cache -O LOCAL_VERSION "$VERSION_URL"
    VERSION=$(cat "LOCAL_VERSION")
    echo "**** Downloading version ${VERSION} ****"
    __DURL="https://github.com/mynttt/UpdateTool/releases/download/${VERSION}/UpdateTool-${VERSION}.jar"
    wget -O tool.jar "$__DURL" || exit 1
fi