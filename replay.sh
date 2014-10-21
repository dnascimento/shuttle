#!/bin/bash
export MAVEN_OPTS="-Xmx5024m -javaagent:object-explorer.jar"
mvn exec:java -Dexec.mainClass="pt.inesc.replay.ReplayNode"