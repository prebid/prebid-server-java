# Vexing

Server-side Header Bidding solution written in [Vert.x](http://vertx.io/).

## Development

Build and run fat JAR:
```shell
mvn clean package
java -jar target/vexing-0.0.1-SNAPSHOT-fat.jar
```

With some optimizations:
```shell
java -jar target/vexing-0.0.1-SNAPSHOT-fat.jar \
  --instances `sysctl -n hw.ncpu` -server \
  -Dvertx.disableWebsockets=true -Dvertx.flashPolicyHandler=false \
  -Dvertx.threadChecks=false -Dvertx.disableContextTimings=true \
  -Dvertx.disableTCCL=true \
  -XX:+UseG1GC -XX:+UseNUMA -XX:+UseParallelGC -XX:+AggressiveOpts
```

If you have the `vertx` binary on your path, you can also start with
```shell
vertx run src/main/java/org/rtb/vexing/Application.java
```
