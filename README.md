# Prebid Server

Server-side Header Bidding solution.

The goal is to allow [Prebid](http://prebid.org/) to offload processing from the
browser to improve performance and end-user responsiveness.

## Usage

When running, the server responds to several HTTP endpoints.

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

##### More detailed project documentation can be found [here](docs/TOC.md).
