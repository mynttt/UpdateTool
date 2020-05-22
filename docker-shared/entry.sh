#!/bin/bash
cd /usr/app
./bootstrap.sh
cd /config
PLEX_DATA_DIR="/plexdata"
export PLEX_DATA_DIR
RUN_EVERY_N_HOURS=${RUN_EVERY_N_HOURS:="12"}
if [ -z "$JVM_MAX_HEAP" ]; then
    __heap="-Xmx256m"
else 
    __heap="-Xmx${JVM_MAX_HEAP}"
fi
echo "MAX JVM HEAP: ${__heap}"
echo "**** Invoking tool! Logs in /config ****"
java -Xms64m "${__heap}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -jar /usr/app/tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
