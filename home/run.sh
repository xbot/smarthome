#!/bin/bash

CHECK_REGEX="python2\s\.\/main\.py"
COMMAND="nohup ./main.py > /dev/null 2>&1"

IsRunning()
{
    if [[ $# -ne 1 ]] || [ -z "$1" ]; then
        echo "IsRunning(): Invalid params." >&2
        return 1
    fi

    if ps aux|grep -P "$1"|grep -v grep|grep -v "$$" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

if [ $# -gt 0 ]; then
    if [ $# -ne 2 ]; then
        echo "Supervisor: Invalid params." >&2
        exit 1
    else
        CHECK_REGEX="$1"
        COMMAND="$2"
    fi
fi

while true; do
    if ! IsRunning "$CHECK_REGEX"; then
        echo "Supervisor: Restarting."
        $COMMAND &
    else
        echo "Supervisor: baby is healthy."
    fi
    sleep 3
done
