# Full list of application configuration options

This document describes all configuration properties available for Prebid Server.

## Spring
- `spring.main.banner-mode` - determine if the banner has to be printed on System.out (console), using the configured logger (log) or not at all (off).

This section can be extended against standard [Spring configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-spring-application.html) options.

## Vert.x
- `vertx.worker-pool-size` - set the maximum number of worker threads to be used by the Vert.x instance.
- `vertx.verticle.instances` - how many verticles should be started. 
This parameter affects how many CPU cores will be utilized by the application. Rough assumption - one verticle instance will keep 1 CPU core busy.
- `vertx.verticle.deploy-timeout-ms` - waiting time before Vert.x starts one verticle instance. If time exceeds - exception will be thrown and Prebid Server stops.

## HTTP
- `http.port` - the port to listen on.

## HTTP Client
- `http-client.max-pool-size` - set the maximum pool size for outgoing connections.
- `http-client.connect-timeout-ms` - set the connect timeout.

## Auction
- `auction.default-timeout-ms` - default operation timeout for OpenRTB Auction and Amp requests.
- `auction.max-request-size` - set the maximum size in bytes of OpenRTB Auction request.
- `auction.stored-requests-timeout-ms` - timeout for stored requests fetching.
- `auction.expected-cache-time-ms` - approximate value in milliseconds for Cache Service interacting. 
This time will be subtracted from global timeout.

## Adapters
- `adapters.*` - the section for bidder specific configuration options.

There are two typical keys:
- `adapters.<BIDDER_NAME>.endpoint` - the url for submitting bids.
- `adapters.<BIDDER_NAME>.usersync-url` - the url for synchronizing UIDs cookie.

But feel free to add additional specific bidder options.

## Metrics
- `metrics.metricType` - set the type of metric counter for [Dropwizard Metrics](http://metrics.dropwizard.io). Can be `flushingCounter` (default), `counter` or `meter`.
- `metrics.type` - define where metrics will be submitted. Available options: `graphite`, `influxdb`.

For `graphite` backend type available next options:
- `metrics.prefix` - the prefix of all metric names.
- `metrics.host` - host:port for sending statistics.
- `metrics.interval` - interval in seconds between successive sending statistics.

For `influxdb` backend type available next options:
- `metrics.prefix` - the prefix of all metric names.
- `metrics.protocol` - external service destination protocol.
- `metrics.host` - the influxDb `host:port` for sending metrics.
- `metrics.database` - the influxDb database to write metrics.
- `metrics.auth` - the authorization string to be used to connect to InfluxDb, of format `username:password`.
- `metrics.connectTimeout` - the connect timeout.
- `metrics.readTimeout` - the response timeout.
- `metrics.interval` - interval in seconds between successive sending metrics.

## Cache
- `cache.scheme` - set the external Cache Service protocol: `http`, `https`, etc.
- `cache.host` - set the external Cache Service destination in format `host:port`.
- `cache.query` - appends to `/cache` as query string params (used for legacy Auction requests).

## Data Cache
- `datacache.type` - where preconfigured application settings must be fetched from. Can be: `filesystem`, `mysql` or `postgres`.

For `filesystem` type available next options:
- `datacache.filename` - location of file settings.

For `mysql` or `postgres` type available next options:
- `datacache.cache-size` - the size of LRU cache.
- `datacache.ttl-seconds` - how log in seconds cache data will be available in LRU cache.

## Stored Requests
- `stored-requests.type` - where stored requests must be fetched from. Can be: `filesystem`, `mysql` or `postgres`.

For `filesystem` type available next options:
- `stored-requests.configpath` - location of file with saved stored requests.

For `mysql` or `postgres` type available next options:
- `stored-requests.host` - database destination in format `host:port`.
- `stored-requests.dbname` - database name.
- `stored-requests.user` - database user.
- `stored-requests.password` - database password.
- `stored-requests.pool-size` - set the initial/min/max pool size of database connections.
- `stored-requests.in-memory-cache.ttl-seconds` - set the maximum response time.
- `stored-requests.in-memory-cache.cache-size` - set the size of LRU cache.
- `stored-requests.query` - the SQL query to fetch stored requests.
- `stored-requests.amp-query` - the SQL query to fetch AMP stored requests.

## Host Cookie
- `host-cookie.optout-cookie.name` - set the cookie name for optout checking.
- `host-cookie.optout-cookie.value` - set the cookie value for optout checking.
- `host-cookie.opt-out-url` - set the url for user redirect in case of opt out.
- `host-cookie.opt-in-url` - set the url for user redirect in case of opt in.
- `host-cookie.family` - set the family name value for host cookie.
- `host-cookie.cookie-name` - set the name value for host cookie.
- `host-cookie.domain` - set the domain value for host cookie.

## Google Recaptcha
- `recaptcha-url` - the url for Google Recaptcha service to submit user verification.
- `recaptcha-secret` - Google Recaptcha secret string given to certain domain account.

## General settings
- `external-url` - the setting stands for external URL prebid server is reachable by, 
for example address of the load-balancer e.g. http://prebid.host.com.
- `default-timeout-ms` - this setting controls default timeout for /auction endpoint.
