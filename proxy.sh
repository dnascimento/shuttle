#!/bin/sh
if [ "$#" -ne 3 ]; then
    echo "Ilegal number of args: <frontend-port> <backend> <port>"
    exit
fi
export JAVA_OPTS="-Xmx5024m -javaagent:object-explorer.jar"

mvn exec:java -Dexec.mainClass="pt.inesc.proxy.Proxy" -Dexec.args="$1 $2 $3"
