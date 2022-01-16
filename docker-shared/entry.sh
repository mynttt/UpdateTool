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

if [ ! -z "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" ]; then
    if [[ "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" == "true" ]]; then
        echo "Enabled native Plex SQLite binary for use in write access!"
        USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS="/usr/app/plexsqlitedriver/Plex SQLite"
        export USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS
    else
        unset USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS
    fi
fi

if [ ! -z "$DOCKER_DEBUG_PRINT_TREE_OF_PLEX_PATHS" ]; then
    if [ ! -z "$PLEX_DATA_DIR" ]; then
        echo "Plex data dir (PLEX_DATA_DIR) => ${PLEX_DATA_DIR}"
	echo "Printing tree..."
	tree "${PLEX_DATA_DIR}"
	echo "==================="
    else
        echo "WARNING: PLEX_DATA_DIR IS NOT SET !!!"
    fi
    if [ ! -z "$OVERRIDE_DATABASE_LOCATION" ]; then
        echo "Override database location will be used to search for DB (OVERRIDE_DATABASE_LOCATION) => ${OVERRIDE_DATABASE_LOCATION}"
	echo "Printing tree..."
	tree "${OVERRIDE_DATABASE_LOCATION}"
	echo "==================="
    else
        echo "Not using OVERRIDE_DATABASE_LOCATION as this has not been set."
    fi
fi

echo "**** Invoking tool! Logs in /config ****"
java -Xms64m "${__heap}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -jar /usr/app/tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
