FROM --platform=amd64 tomcat:8.5-jdk8-temurin-jammy

ENV VERTICLE_FILE prebid-server.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

#EXPOSE 5000

# Copy your fat jar to the container
COPY ./target/$VERTICLE_FILE $VERTICLE_HOME/
RUN mkdir -p /var/log/mbid/

# Launch the verticle
WORKDIR $VERTICLE_HOME
# Add Tini
ENV TINI_VERSION v0.18.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static /tini
RUN chmod +x /tini

ENTRYPOINT ["/tini", "--"]


#It will be overwritten
CMD ["exec java -Dlogging.config=logback.xml -jar prebid-server.jar --debug --spring.config.location=classpath:/application.yaml,prebid-config.yaml"]
