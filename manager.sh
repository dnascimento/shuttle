#!/bin/bash
export JAVA_OPTS="-Xmx1G -javaagent:object-explorer.jar"
export MAVEN_OPTS="-Xmx1G -javaagent:object-explorer.jar"

mvn exec:java -Dexec.mainClass="pt.inesc.manager.Manager"
#java -cp "lib/*.jar:target/undo-0.0.1-SNAPSHOT-jar-with-dependencies.jar" -Xmx10G -javaagent:object-explorer.jar pt.inesc.manager.Manager 
