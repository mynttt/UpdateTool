#!/bin/bash

VERSION_URL=https://raw.githubusercontent.com/mynttt/UpdateTool/master/VERSION

if [ ! -f VERSION ]; then
    echo "@@ CRITICAL !!! - NO VERSION FILE FOUND WITHIN IMAGE !!! @@"
    exit 1
else
    wget -O VERSION2 $VERSION_URL 2>&1 | grep "^wget:"
    V_CUR=$(cat VERSION)
    V_REP=$(cat VERSION2)
    if [[ ! "$V_CUR" = "$V_REP" ]]; then
        echo "*** OUTDATED VERSION USED (OR NO INTERNET ACCESS) !!! DOCKER IS ON ${V_CUR} WHILE CURRENT VERSION IS ${V_REP}. PLEASE UPDATE YOUR CONTAINER! ***"
    else
        echo "*** VERSION ${V_CUR} IS UP TO DATE! ***"
    fi
    rm VERSION2
fi
