#!/bin/bash

set -e

VAR=$1

status() {
    echo "status"
}

update() {
    echo "update"
}

start() {
    echo "start"
}

stop() {
    echo "stop"
}

case "$VAR" in

    status)
        status
        ;;

    update)
        update
        ;;

    start)
        start
        ;;

    stop)
        stop
        ;;

    *)
        echo "Invalid option -> (start|stop|status|update)"
        ;;
esac