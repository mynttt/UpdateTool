#!/bin/bash

JAVA_ID="c01bbaaa-1da9-4c4d-b4ed-23a2d59abca1"
WRAPPER_ID="ee59bb6d-0a4a-4bb8-b1a6-69dc1c1c2803"
LOG_FILE="updatetool-wrapper.log"
SCRIPT_DIR="$( dirname -- "$BASH_SOURCE"; )";

VAR=$1

status() {
    w_pid=$(pgrep -f "$WRAPPER_ID")
    j_pid=$(pgrep -f "$JAVA_ID")

    if [[ -z $w_pid ]]; then
        echo "Wrapper is not running."
    else
        echo "Wrapper is running @ $w_pid"
    fi

    if [[ -z $j_pid ]]; then
        echo "UpdateTool is not running."
    else
        echo "UpdateTool is running @ $j_pid"
    fi

    if [[ ! -z $j_pid || ! -z $w_pid ]]; then
        echo "Log-file: ${SCRIPT_DIR}/${LOG_FILE}"
    fi
}

start() {
    w_pid=$(pgrep -f "$WRAPPER_ID")
    j_pid=$(pgrep -f "$JAVA_ID")

    if [[ -z $w_pid || -z $j_pid ]]; then
        echo "Only the Wrapper or UpdateTool are running -> Restarting both as precaution..."
        kill -9 $w_pid > /dev/null 2>&1
        kill -9 $j_pid > /dev/null 2>&1
        nohup "${SCRIPT_DIR}/dependencies/wrapper.sh" "${SCRIPT_DIR}" $WRAPPER_ID > "${SCRIPT_DIR}/$LOG_FILE" 2>&1 &
        echo "Wrapper started as background process."
    else
        echo "UpdateTool and Wrapper already running."
    fi
}

stop() {
    w_pid=$(pgrep -f "$WRAPPER_ID")
    j_pid=$(pgrep -f "$JAVA_ID")
    
    if [[ ! -z $w_pid || ! -z $j_pid ]]; then
        kill -9 $w_pid > /dev/null 2>&1
        kill -9 $j_pid > /dev/null 2>&1
        echo "UpdateTool and Wrapper stopped."
    else
        echo "UpdateTool and Wrapper are not running."
    fi
}

update() {
    echo "Attempting stop..."
    stop
    echo "Bootstrapping SQLite3 binary... (u-sql3.log)"
    cd "${SCRIPT_DIR}/updatetool"
    "${SCRIPT_DIR}/dependencies/bootstrap_plex_binary.sh" > "${SCRIPT_DIR}/u-sql3.log" 2>&1
    echo "Bootstrapping UpdateTool JAR... (u-jar.log)"
    "${SCRIPT_DIR}/dependencies/bootstrap_updatetool_jar.sh" > "${SCRIPT_DIR}/u-jar.log" 2>&1
    echo "*********************"
    echo "UPDATE DONE"
    echo "*********************"
    echo "Attempting start..."
    cd "${SCRIPT_DIR}"
    start
}

restart() {
    echo "Attempting stop..."
    stop
    echo "Attempting start..."
    start
}

case "$VAR" in

    status)
        status
        ;;

    update)
        update
        ;;

    restart)
        restart
        ;;

    start)
        start
        ;;

    stop)
        stop
        ;;

    *)
        echo "Invalid option -> (start|stop|restart|status|update)"
        ;;
esac
