#!/bin/bash

set -e

VERSION_URL=https://raw.githubusercontent.com/mynttt/UpdateTool/master/FLAVOUR
FLAVOUR="null"

echo "test"

if [ -f LOCAL_FLAVOUR ]; then
    wget --no-cache -O TMP_FLAVOUR "$VERSION_URL"
    FLAVOUR=$(cat "TMP_FLAVOUR")
    FLAVOUR_LOCAL=$(cat "LOCAL_FLAVOUR")
    if [ $FLAVOUR != $FLAVOUR_LOCAL ]; then
        echo "**** Updating Plex SQLite3 Binary to ${FLAVOUR} ****"
    else
        echo "**** Plex SQLite3 Binary is up to date! Nothing to do. ****"
        exit 0
    fi
else
    wget --no-cache -O TMP_FLAVOUR "$VERSION_URL"
    FLAVOUR=$(cat "TMP_FLAVOUR")
    echo "**** Flavour for Plex SQLite3 binary: ${FLAVOUR} ****"
fi

rm -rf plex_sqlite
mkdir -p plex_sqlite
cd plex_sqlite

dl_url="https://downloads.plex.tv/plex-media-server-new/${FLAVOUR}/debian/plexmediaserver_${FLAVOUR}_amd64.deb"

# Extract
wget -v -O plex_tmp "$dl_url"
../../Dependencies/ar vx plex_tmp
tar -xf data.tar.xz
rm plex_tmp debian-binary _gpgplex control.tar.gz data.tar.xz
mv "usr/lib/plexmediaserver" "plexsqlitedriver"
rm -rf etc usr
cd plexsqlitedriver
rm  CrashUploader 'Plex Media Fingerprinter' 'Plex Relay' 'Plex Transcoder' 'Plex Commercial Skipper' 'Plex Media Scanner' 'Plex Tuner Service' 'Plex DLNA Server' 'Plex Script Host'
rm -rf Resources etc
rm -rf "lib/dri"

# Testing functionality
OUTPUT=$(echo "select sqlite_version();" | "./Plex SQLite")

if [[ "$OUTPUT" == "3"* ]]; then
    echo $FLAVOUR > "../../LOCAL_FLAVOUR"
    echo "PLEX_SQLITE_DEPLOYED @ ${PWD}"
    echo 
else
    echo "PLEX_SQLITE_DEPLOYMENT_ERROR"
    echo "$OUTPUT"
    exit -1
fi