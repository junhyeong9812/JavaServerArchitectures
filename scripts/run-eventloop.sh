#!/bin/bash
echo "Starting Event Loop Server..."
java -cp build/classes:lib/* \
     -Djava.util.logging.config.file=config/logging.properties \
     -Dserver.config=config/eventloop-server.properties \
     com.serverarch.eventloop.EventLoopServer