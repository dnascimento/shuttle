#!/bin/bash
export JAVA_OPTS="-Xmx5024m -javaagent:object-explorer.jar"
export MAVEN_OPTS="-Xmx5024m -javaagent:object-explorer.jar"
echo "Dario Thesis controller:"
mvn test -P$1
