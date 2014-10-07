#!/bin/bash

mvn clean compile assembly:single


-server -Xms3G -Xmx5G -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=3333







java -server -Xms3G -Xmx5G -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=1100 -Djava.rmi.server.hostname=54.86.210.30 -jar target/undo-0.0.1-SNAPSHOT-jar-with-dependencies.jar 9000 8080 server