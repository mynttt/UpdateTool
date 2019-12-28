#!/bin/bash

VERSION_URL=https://raw.githubusercontent.com/mynttt/PlexImdbUpdateTool/master/VERSION

function download() {
    VERSION=$1
    echo "**** Downloading version ${VERSION} ****"
    __DURL="https://github.com/mynttt/UpdateTool/releases/download/${VERSION}/UpdateTool-${VERSION}.jar"
    wget -O tool.jar $__DURL #2>&1 | grep "^wget:"
}

if [ ! -f VERSION ]; then
    wget -O VERSION $VERSION_URL 2>&1 | grep "^wget:"
    download $(cat VERSION)
else
    wget -O VERSION2 $VERSION_URL 2>&1 | grep "^wget:"
    if [[ ! $(cat VERSION) = $(cat VERSION2) ]]; then
        download $(cat VERSION2)
        mv VERSION2 VERSION
    else
        rm VERSION2
    fi
fi