FROM alpine/git as clone
WORKDIR /app
RUN git clone https://github.com/rubicon-project/prebid-server-java.git

FROM maven:3.6.0-jdk-8-slim AS build
WORKDIR /app
COPY --from=clone /app/prebid-server-java /app
RUN mvn -Dmaven.test.skip=true clean package

FROM openjdk:8-jre-alpine
WORKDIR /app
COPY --from=clone /app/prebid-server-java/sample /app/sample
COPY --from=build /app/target/prebid-server.jar /app
EXPOSE 8080
EXPOSE 8000
CMD ["java", "-jar", "prebid-server.jar", "--spring.config.additional-location=sample/prebid-config.yaml"]
