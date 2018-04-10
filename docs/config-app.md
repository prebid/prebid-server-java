# Full list of application configuration options

This document describes all configuration properties available for Prebid Server.

## Spring
- `spring.main.banner-mode` - determine if the banner has to be printed on System.out (console), using the configured logger (log) or not at all (off).

This section can be extended against standard [Spring configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-spring-application.html) options.

## Vert.x
- `vertx.worker-pool-size` - set the maximum number of worker threads to be used by the Vert.x instance.
- `vertx.uploads-dir` - directory that Vert.x [BodyHandler](http://vertx.io/docs/apidocs/io/vertx/ext/web/handler/BodyHandler.html) will use to store multi-part file uploads. 
This parameter exists to allow to change the location of the directory Vert.x will create because it will and there is no way to make it not.
- `vertx.verticle.instances` - how many verticles should be started. 
This parameter affects how many CPU cores will be utilized by the application. Rough assumption - one verticle instance will keep 1 CPU core busy.
- `vertx.verticle.deploy-timeout-ms` - waiting time before Vert.x starts one verticle instance. If time exceeds - exception will be thrown and Prebid Server stops.

## HTTP
- `http.port` - the port to listen on.

## HTTP Client
- `http-client.max-pool-size` - set the maximum pool size for outgoing connections.
- `http-client.connect-timeout-ms` - set the connect timeout.

## Auction
- `auction.default-timeout-ms` - default operation timeout for OpenRTB Auction requests.
- `auction.max-request-size` - set the maximum size in bytes of OpenRTB Auction request.
- `auction.stored-requests-timeout-ms` - timeout for stored requests fetching.
- `auction.expected-cache-time-ms` - approximate value in milliseconds for Cache Service interacting. 
This time will be subtracted from global timeout.

## Amp
- `amp.default-timeout-ms` - default operation timeout for OpenRTB Amp requests.
- `amp.custom-targeting` - a list of bidders whose custom targeting should be included in AMP responses.

## Adapters
- `adapters.*` - the section for bidder specific configuration options.

There are several typical keys:
- `adapters.<BIDDER_NAME>.enabled` - indicates the bidder should be active and ready for auction. By default all bidders are disabled.
- `adapters.<BIDDER_NAME>.endpoint` - the url for submitting bids.
- `adapters.<BIDDER_NAME>.usersync-url` - the url for synchronizing UIDs cookie.

But feel free to add additional bidder's specific options.

## Metrics
- `metrics.metricType` - set the type of metric counter for [Dropwizard Metrics](http://metrics.dropwizard.io). Can be `flushingCounter` (default), `counter` or `meter`.

Metrics can be submitted simultaneously to many backends. Currently we support `graphite` and `influxdb`.

For `graphite` backend type available next options:
- `metrics.graphite.prefix` - the prefix of all metric names.
- `metrics.graphite.host` - the graphite host for sending statistics.
- `metrics.graphite.port` - the graphite port for sending statistics.
- `metrics.graphite.interval` - interval in seconds between successive sending metrics.

For `influxdb` backend type available next options:
- `metrics.influxdb.prefix` - the prefix of all metric names.
- `metrics.influxdb.protocol` - external service destination protocol.
- `metrics.influxdb.host` - the influxDb host for sending metrics.
- `metrics.influxdb.port` - the influxDb port for sending metrics.
- `metrics.influxdb.database` - the influxDb database to write metrics.
- `metrics.influxdb.auth` - the authorization string to be used to connect to InfluxDb, of format `username:password`.
- `metrics.influxdb.connectTimeout` - the connect timeout.
- `metrics.influxdb.readTimeout` - the response timeout.
- `metrics.influxdb.interval` - interval in seconds between successive sending metrics.

## Cache
- `cache.scheme` - set the external Cache Service protocol: `http`, `https`, etc.
- `cache.host` - set the external Cache Service destination in format `host:port`.
- `cache.query` - appends to `/cache` as query string params (used for legacy Auction requests).

## Application settings (account configuration, stored ad unit configurations, stored requests)
- `settings.type` - where preconfigured application settings must be fetched from. Can be: `filesystem`, `mysql` or `postgres`.

For `filesystem` type available next options:
- `settings.settings-filename` - location of file settings.
- `settings.stored-requests-dir` - directory with stored requests.

For `mysql` or `postgres` type available next options:
- `settings.host` - database destination host.
- `settings.port` - database destination port.
- `settings.dbname` - database name.
- `settings.user` - database user.
- `settings.password` - database password.
- `settings.pool-size` - set the initial/min/max pool size of database connections.
- `settings.in-memory-cache.ttl-seconds` - how log in seconds cache data will be available in LRU cache.
- `settings.in-memory-cache.cache-size` - the size of LRU cache.
- `settings.stored-requests-query` - the SQL query to fetch stored requests.
- `settings.amp-stored-requests-query` - the SQL query to fetch AMP stored requests.

## Host Cookie
- `host-cookie.optout-cookie.name` - set the cookie name for optout checking.
- `host-cookie.optout-cookie.value` - set the cookie value for optout checking.
- `host-cookie.opt-out-url` - set the url for user redirect in case of opt out.
- `host-cookie.opt-in-url` - set the url for user redirect in case of opt in.
- `host-cookie.family` - set the family name value for host cookie.
- `host-cookie.cookie-name` - set the name value for host cookie.
- `host-cookie.domain` - set the domain value for host cookie.
- `host-cookie.ttl-days` - set the cookie ttl in days

## Google Recaptcha
- `recaptcha-url` - the url for Google Recaptcha service to submit user verification.
- `recaptcha-secret` - Google Recaptcha secret string given to certain domain account.

## General settings
- `external-url` - the setting stands for external URL prebid server is reachable by, 
for example address of the load-balancer e.g. http://prebid.host.com.
- `default-timeout-ms` - this setting controls default timeout for /auction endpoint.
