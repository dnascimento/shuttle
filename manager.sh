#!/bin/bash
export JAVA_OPTS="-Xmx5024m -javaagent:object-explorer.jar"
export MAVEN_OPTS="-Xmx5024m -javaagent:object-explorer.jar"

mvn exec:java -Dexec.mainClass="pt.inesc.manager.Manager"
#java -cp ":target/undo-0.0.1-SNAPSHOT-jar-with-dependencies.jar" -Xmx5G -javaagent:object-explorer.jar pt.inesc.manager.Manager 
