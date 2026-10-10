#!/bin/sh
set -eu

# The mounted volume can initially be root-owned. The application itself runs as dbgram.
mkdir -p /app/uploads
chown dbgram:dbgram /app/uploads
exec gosu dbgram java -XX:MaxRAMPercentage=50 -jar /app/server.jar
