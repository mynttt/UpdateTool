#!/bin/bash
JAVA_ID="c01bbaaa-1da9-4c4d-b4ed-23a2d59abca1"
SCRIPT_DIR="$1"

. "${SCRIPT_DIR}/config.sh"
cd "${SCRIPT_DIR}/updatetool"
"${SCRIPT_DIR}/dependencies/bootstrap_updatetool_jar.sh" > /dev/null 2>&1

echo "MAX JVM HEAP: ${JVM_MAX_HEAP}"

if [ -z "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" ]; then
    echo "Update: 18.07.2022 - Forcing unset variable \$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS to 'true' in order to minimize accidental database corruptions."
    echo "If you for whatever reason don't want this set it to anything else than 'true'."
    USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS="true"
fi

if [[ "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" == "true" ]]; then
    echo "Enabled native Plex SQLite binary for use in write access!"
    USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS="${SCRIPT_DIR}/updatetool/plex_sqlite/plexsqlitedriver/Plex SQLite"
    export USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS
else
    echo "\$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS has been explicitly disabled! This is a potentially dangerous operation that can corrupt your database! Unset it or set it to 'true' in order to reverse this!"
    unset USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS
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


cd "${SCRIPT_DIR}/updatetool" 
echo "**** Invoking tool! Logs in ${SCRIPT_DIR}/updatetool ****"

if [ ! -z "$RESTART_ON_CRASH" ]; then
    if [[ "$RESTART_ON_CRASH" == "true" ]]; then
	echo "**** \$RESTART_ON_CRASH set to true => update tool will restart automatically within 180 seconds after encountering a crash. ****"
	while true
	do
	    "jdk/bin/java" -Xms64m "-Xmx${JVM_MAX_HEAP}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -Djavaid="$JAVA_ID" -jar tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
	    echo "**** Binary has crashed. Restart in 180 seconds... ****"
	    sleep 180
	done
    else
        echo "**** \$RESTART_ON_CRASH not set to true => container will shutdown with tool exit. ****"
        "jdk/bin/java" -Xms64m "-Xmx${JVM_MAX_HEAP}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -Djavaid="$JAVA_ID" -jar tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
    fi
else
    echo "**** \$RESTART_ON_CRASH not set to true => container will shutdown with tool exit. ****"
    "jdk/bin/java" -Xms64m "-Xmx${JVM_MAX_HEAP}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -Djavaid="$JAVA_ID" -jar tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
fi
