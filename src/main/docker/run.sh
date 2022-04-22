#!/bin/sh

exec java \
  -Dvertx.cacheDirBase=/app/prebid-server/data/.vertx \
  -Dspring.config.additional-location=/app/prebid-server/,/app/prebid-server/conf/ \
  ${JAVA_OPTS} \
  -jar \
  /app/prebid-server/prebid-server.jar
