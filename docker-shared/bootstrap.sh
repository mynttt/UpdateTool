#!/bin/bash

VERSION_URL=https://raw.githubusercontent.com/mynttt/UpdateTool/master/VERSION

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
    V_CUR=$(cat VERSION)
    V_REP=$(cat VERSION2)
    if [[ ! "$V_CUR" = "$V_REP" ]]; then
        echo "*** OUTDATED VERSION USED !!! DOCKER IS ON ${V_CUR} WHILE CURRENT VERSION IS ${V_REP}. PLEASE UPDATE YOUR CONTAINER! ***"
    else
        echo "*** VERSION ${V_CUR} IS UP TO DATE! ***"
    fi
    rm VERSION2
fi
