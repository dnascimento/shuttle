#!/bin/sh
if [ "$#" -ne 3 ]; then
    echo "Ilegal number of args: <frontend-port> <backend-port> <backend-address>"
    exit
fi

mvn exec:java -Dexec.mainClass="pt.inesc.proxy.Proxy" -Dexec.args="$1 $2 $3"
