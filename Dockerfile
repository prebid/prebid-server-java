FROM openjdk:11

WORKDIR /app/prebid-server

VOLUME /app/prebid-server/conf
VOLUME /app/prebid-server/data

COPY src/main/docker/run.sh ./
COPY src/main/docker/application.yaml ./
COPY target/prebid-server.jar ./

EXPOSE 8050
EXPOSE 8060

ENTRYPOINT [ "/app/prebid-server/run.sh" ]
