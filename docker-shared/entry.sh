#!/bin/bash
cd /usr/app
./bootstrap.sh
cd /config
PLEX_DATA_DIR="/plexdata"
export PLEX_DATA_DIR
RUN_EVERY_N_HOURS=${RUN_EVERY_N_HOURS:="24"}

D_OS_ARCH=$(dpkg --print-architecture)

case $D_OS_ARCH in
  "i386")
  export HACK_TO_BYPASS_THIS_ON_ARM_BUILDS="yes"
  ;;
  "armhf")
  export HACK_TO_BYPASS_THIS_ON_ARM_BUILDS="yes"
  ;;
  "arm64")
  export HACK_TO_BYPASS_THIS_ON_ARM_BUILDS="yes"
  ;;
  *)
  echo "OS OK: NO BYPASS REQUIRED"
  ;;
esac

if [ -z "$JVM_MAX_HEAP" ]; then
    __heap="-Xmx256m"
else 
    __heap="-Xmx${JVM_MAX_HEAP}"
fi

echo "MAX JVM HEAP: ${__heap}"

if [ -z "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" ]; then
    echo "Update: 18.07.2022 - Forcing unset variable \$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS to 'true' in order to minimize accidental database corruptions."
    echo "If you for whatever reason don't want this set it to anything else than 'true'."
    USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS="true"
fi

if [[ "$USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS" == "true" ]]; then
    echo "Enabled native Plex SQLite binary for use in write access!"
    USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS="/usr/app/plexsqlitedriver/Plex SQLite"
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

echo "**** Invoking tool! Logs in /config ****"

if [ ! -z "$RESTART_ON_CRASH" ]; then
    if [[ "$RESTART_ON_CRASH" == "true" ]]; then
	echo "**** \$RESTART_ON_CRASH set to true => update tool will restart automatically within 10 seconds after encountering a crash. ****"
	while true
	do
	    java -Xms64m "${__heap}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -jar /usr/app/tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
	    echo "**** Binary has crashed. Restart in 10 seconds... ****"
	    sleep 10
	done
    else
        echo "**** \$RESTART_ON_CRASH not set to true => container will shutdown with tool exit. ****"
        java -Xms64m "${__heap}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -jar /usr/app/tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
    fi
else
    echo "**** \$RESTART_ON_CRASH not set to true => container will shutdown with tool exit. ****"
    java -Xms64m "${__heap}" -XX:+UseG1GC -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=30 -jar /usr/app/tool.jar imdb-docker "{schedule=$RUN_EVERY_N_HOURS}"
fi
