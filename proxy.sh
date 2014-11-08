#!/bin/sh

if [ "$#" -ne 3 ]; then
    echo "Ilegal number of args: <frontend-port> <backend> <port>"
    exit
fi
export JAVA_OPTS="-Xmx5G"
export MAVEN_OPTS="-Xmx5G"
mvn exec:java -Dexec.mainClass="pt.inesc.proxy.Proxy" -Dexec.args="$1 $2 $3"
