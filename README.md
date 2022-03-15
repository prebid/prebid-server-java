### This is the Java version of Prebid Server. See the Prebid Server [Feature List](https://docs.prebid.org/prebid-server/features/pbs-feature-idx.html) and [FAQ entry](https://docs.prebid.org/faq/prebid-server-faq.html#why-are-there-two-versions-of-prebid-server-are-they-kept-in-sync) to understand the differences between PBS-Java and [PBS-Go](https://github.com/prebid/prebid-server).

# Prebid Server (Java)

[![GitHub version](https://badge.fury.io/gh/prebid%2fprebid-server-java.svg)](http://badge.fury.io/gh/prebid%2fprebid-server-java)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/prebid/prebid-server-java.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/prebid/prebid-server-java/context:java)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/prebid/prebid-server-java.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/prebid/prebid-server-java/alerts/)
[![GitHub contributors](https://img.shields.io/github/contributors/prebid/prebid-server-java.svg)](https://GitHub.com/prebid/prebid-server-java/contributors/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/prebid/prebid-server-java/blob/master/docs/contributing.md) 
[![GitHub pull-requests closed](https://img.shields.io/github/issues-pr-closed/prebid/prebid-server-java.svg)](https://GitHub.com/prebid/prebid-server-java/pull/)

Prebid Server is an open source implementation of Server-Side Header Bidding.
It is managed by Prebid.org,
and upholds the principles from the [Prebid Code of Conduct](https://prebid.org/wrapper_code_of_conduct.html).

This project does not support the same set of Bidders as Prebid.js, although there is overlap.
The current set can be found in the [adapters](./src/main/java/org/prebid/server/bidder) package. If you don't see the one you want, feel free to [contribute it](docs/developers/add-new-bidder.md).

For more information, see:

- [What is Prebid?](https://prebid.org/why-prebid/)
- [Getting started with Prebid Server](https://docs.prebid.org/prebid-server/overview/prebid-server-overview.html)
- [Current Bidders](https://docs.prebid.org/dev-docs/pbs-bidders.html)

Please consider [registering your Prebid Server](https://docs.prebid.org/prebid-server/hosting/pbs-hosting.html#optional-registration) to get on the mailing list for updates, etc.

# Getting Started

The server makes the following assumptions:
- No ranking or decisioning is performed by this server. It just proxies requests.
- No ad quality management (e.g., malware, viruses, deceptive creatives) is performed by this server.
- This server does no fraud scanning and does nothing to prevent bad traffic.
- This server logs errors but not requests.
- This server has no user profiling or user data collection capabilities.

This project is built upon [Vert.x](http://vertx.io) to achieve high request throughput. 
We use [Maven](https://maven.apache.org) and attempt to introduce minimal dependencies.

When running, the server responds to several HTTP [endpoints](docs/endpoints).

## Building

Follow next steps to create JAR file which can be deployed locally.

- Download or clone a project:
```bash
git clone https://github.com/prebid/prebid-server-java.git
```

- Move to project directory:
```bash
cd prebid-server-java
```

And from this step there are two common use cases, which can be chosen depending on your goals 

1. Create prebid-server JAR only
- Run below command to build project:
```bash
mvn clean package
```

2. Create prebid-server JAR with modules
- Run below command to build project:
```bash
mvn clean package --file extra/pom.xml
```
For more information how to configure the server follow [documentation](docs/build.md).

## Configuration

The source code includes minimal required configuration file `sample/prebid-config.yaml`.
Also, check the account settings file `sample/sample-app-settings.yaml`.

For more information how to configure the server follow [documentation](docs/config.md).


## Running

Run your local server with the command:
```bash
java -jar target/prebid-server.jar --spring.config.additional-location=sample/prebid-config.yaml
```

For more options how to start the server, please follow [documentation](docs/run.md).

## Verifying

To check the server is started go to [http://localhost:8080/status](http://localhost:8080/status) 
and verify response status is `200 OK`.

# Documentation

## Development
- [Endpoints](https://docs.prebid.org/prebid-server/endpoints/pbs-endpoint-overview.html)
- [Adding new bidder](https://docs.prebid.org/prebid-server/developers/add-new-bidder-java.html)
- [Adding new analytics module](https://docs.prebid.org/prebid-server/developers/pbs-build-an-analytics-adapter.html#adding-an-analytics-adapter-in-pbs-java)
- [Adding viewability support](docs/developers/add-viewability-vendors.md)
- [Auction result post-processing](docs/auction-result-post-processing.md)
- [Cookie Syncs](https://docs.prebid.org/prebid-server/developers/pbs-cookie-sync.html)
- [Stored Requests](docs/developers/stored-requests.md)
- [Unit Tests](docs/developers/unit-tests.md)
- [GDPR](docs/gdpr.md)

## Maintenance
- [Build for local](docs/build.md)
- [Build for AWS](docs/build-aws.md)
- [Configure application](docs/config.md)
  - [Full list of configuration options](docs/config-app.md)
  - [Application settings](docs/application-settings.md)
- [Run with optimizations](docs/run.md)
- [Metrics](docs/metrics.md)

## Contributing
- [Contributing](docs/developers/contributing.md)
- [Code Style](docs/developers/code-style.md)
- [Code Review](docs/developers/code-reviews.md)
- [Versioning](docs/developers/versioning.md)
