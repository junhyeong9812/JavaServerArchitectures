#!/bin/bash
echo "Starting Hybrid Server..."
java -cp build/classes:lib/* \
     -Djava.util.logging.config.file=config/logging.properties \
     -Dserver.config=config/hybrid-server.properties \
     com.serverarch.hybrid.HybridServer