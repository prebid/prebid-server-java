# Vexing

Server-side Header Bidding solution.

The goal is to allow [Prebid](http://prebid.org/) to offload processing from the
browser to improve performance and end-user responsiveness.


## Usage

When running, the server responds to several HTTP endpoints.

### Auction

The `/auction` endpoint expects a JSON request in to format defined by
[pbs_request.json](https://github.com/prebid/prebid-server/blob/master/static/pbs_request.json).

The server makes the following assumptions:
- No ranking or decisioning is performed by this server. It just proxies
requests.
- No ad quality management (e.g., malware, viruses, deceptive creatives) is
performed by this server.
- This server does no fraud scanning and does nothing to prevent bad traffic.
- This server does no logging.
- This server has not user profiling or user data collection capabilities.


## Development

This project is built upon [Vert.x](http://vertx.io/) to achieve high request
throughput. We use Maven and attempt to introduce minimal dependencies.

To develop this project, you will need at least
[Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and [Maven](https://maven.apache.org/) installed.
You may also wish to install the `vertx` command line tool, but it should not be
necessary to build and run the project.

To build a runnable JAR
```shell
mvn clean package
```


### Code Style

The [pom.xml](pom.xml) is configured to enforce a coding style defined in
[checkstyle.xml](checkstyle.xml). The intent here is to maintain a common style
across the project and rely on the process to enforce it instead of individuals.


## Configuration

Vert.x provides a built-in mechanism for reading configuration from a JSON file,
which can be provided at start up.
```shell
java -jar app-fat.jar -conf <path/to/app-conf.json>
```

Example configuration:
```json
{ "http.port": 8080
, "http-client.max-pool-size": 32768
, "http-client.connect-timeout-ms": 1000
}
```


## Running

Build and run fat JAR from the command line:
```shell
mvn clean package
java -jar target/prebid-server-0.0.1-SNAPSHOT-fat.jar
```

Feel free to optimize the command line to suit your environment
(`instances` argument below works on OSX)
```shell
java -jar target/prebid-server-0.0.1-SNAPSHOT-fat.jar \
  --instances `sysctl -n hw.ncpu` -server \
  -Dvertx.disableWebsockets=true -Dvertx.flashPolicyHandler=false \
  -Dvertx.threadChecks=false -Dvertx.disableContextTimings=true \
  -Dvertx.disableTCCL=true \
  -XX:+UseG1GC -XX:+UseNUMA -XX:+UseParallelGC -XX:+AggressiveOpts \
  -conf src/main/conf/prebid-server-conf.json
```

If you have the `vertx` binary on your path, you can also start with
```shell
vertx run src/main/java/org/rtb/vexing/Application.java
```

### Static content run configuration
To override default static content you can create "static" folder in place you running the server (Vert.x will search for this directory in current working directory as seen by java process).

Setting the option "vertx.disableFileCPResolving" to true disables creating cache folder, so it can be used only if you override static content folder because content from JAR file will not be served.

Vert.x caches files that are served from the classpath into a file on disk in a sub-directory of a directory called ".vertx" in the current working directory by default.
You can set another location with "vertx.cacheDirBase" key, for ex:
```shell
-Dvertx.cacheDirBase=/tmp/prebid-server
```
