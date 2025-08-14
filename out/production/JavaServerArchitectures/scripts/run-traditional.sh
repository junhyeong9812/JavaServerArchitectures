#!/bin/bash
echo "Starting Traditional Server..."
java -cp build/classes:lib/* \
     -Djava.util.logging.config.file=config/logging.properties \
     -Dserver.config=config/traditional-server.properties \
     com.serverarch.traditional.TraditionalServer