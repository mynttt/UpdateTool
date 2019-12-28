#!/bin/bash
cd /usr/app
./bootstrap.sh
cd /config
PLEX_DATA_DIR="/plexdata"
export PLEX_DATA_DIR
echo "**** Invoking tool! Logs in /config ****"
java -Xms32m -jar /usr/app/tool.jar imdb-docker $RUN_EVERY_N_HOURS $CLEAR_CACHE_EVERY_N_DAYS
