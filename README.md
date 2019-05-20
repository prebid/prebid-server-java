## This code is being used in production by multiple Prebid.org members, but is not the "official" version. See https://github.com/prebid/prebid-server/

# Prebid Server

Prebid Server is an open source implementation of Server-Side Header Bidding.
It is managed by [Prebid.org](http://prebid.org/overview/what-is-prebid-org.html),
and upholds the principles from the [Prebid Code of Conduct](http://prebid.org/wrapper_code_of_conduct.html).

This project does not support the same set of Bidders as Prebid.js, although there is overlap.
The current set can be found in the [adapters](./src/main/java/org/prebid/server/bidder) package. If you don't see the one you want, feel free to [contribute it](docs/developers/add-new-bidder.md).

For more information, see:

- [What is Prebid?](http://prebid.org/overview/intro.html)
- [Getting started with Prebid Server](http://prebid.org/dev-docs/get-started-with-prebid-server.html)
- [Current Bidders](http://prebid.org/dev-docs/prebid-server-bidders.html)

## Usage

When running, the server responds to several HTTP [endpoints](docs/endpoints).

The server makes the following assumptions:
- No ranking or decisioning is performed by this server. It just proxies requests.
- No ad quality management (e.g., malware, viruses, deceptive creatives) is performed by this server.
- This server does no fraud scanning and does nothing to prevent bad traffic.
- This server does no logging.
- This server has not user profiling or user data collection capabilities.

## Development

This project is built upon [Vert.x](http://vertx.io) to achieve high request throughput. 
We use [Maven](https://maven.apache.org) and attempt to introduce minimal dependencies.

## Getting Started

To start the Prebid Server you need to do the following steps:
- Build all-in-one JAR file from sources as described [here](docs/build.md).
- Check minimal needed configuration file `sample/prebid-config.yaml`.
- Also, check the Data Cache settings file `sample/sample-app-settings.yaml`.
For more information how to configure the server follow [documentation](docs/config.md).

- Run your server with the next command:
```
java -jar target/prebid-server.jar --spring.config.additional-location=sample/prebid-config.yaml
```
For more information how to start the server follow [documentation](docs/run.md).

- To verify everything is OK go to `http://localhost:8080/status` and check response status is `200 OK`.

##### More detailed project documentation can be found [here](docs/TOC.md).
