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
- `auction.expected-cache-time-ms` - approximate value in milliseconds for Cache Service interacting. This time will be subtracted from global timeout.
- `auction.ad-server-currency` - default currency for auction, if its value was not specified in request. Important note: PBS uses ISO 4217 codes for the representation of currencies.
- `auction.currency-rates-refresh-period-ms` - default refresh period for currency rates updates.
- `auction.currency-rates-url` - the url for Prebid.org’s currency file. [More details](http://prebid.org/dev-docs/modules/currency.html)

## Amp
- `amp.default-timeout-ms` - default operation timeout for OpenRTB Amp requests.
- `amp.timeout-adjustment-ms` - reduces timeout value passed in Amp request so that Prebid Server can handle timeouts from adapters and respond to the AMP RTC request before it times out.
- `amp.custom-targeting` - a list of bidders whose custom targeting should be included in AMP responses.

## Adapters
- `adapters.*` - the section for bidder specific configuration options.

There are several typical keys:
- `adapters.<BIDDER_NAME>.enabled` - indicates the bidder should be active and ready for auction. By default all bidders are disabled.
- `adapters.<BIDDER_NAME>.endpoint` - the url for submitting bids.
- `adapters.<BIDDER_NAME>.usersync-url` - the url for synchronizing UIDs cookie.
- `adapters.<BIDDER_NAME>.pbs-enforces-gdpr` - indicates if pbs server provides gdpr support for bidder or bidder will handle it itself.

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
Preconfigured application settings can be obtained from multiple data sources consequently: 
1. Try to fetch from filesystem data source (if configured).
2. Try to fetch from database data source (if configured).
3. Try to fetch from http data source (if configured).

Warning! Application will not start in case of no one data source is defined and you'll get an exception in logs.

For filesystem data source available next options:
- `settings.filesystem.settings-filename` - location of file settings.
- `settings.filesystem.stored-requests-dir` - directory with stored requests.
- `settings.filesystem.stored-imps-dir` - directory with stored imps.

For database data source available next options:
- `settings.database.type` - type of database to be used: `mysql` or `postgres`.
- `settings.database.host` - database destination host.
- `settings.database.port` - database destination port.
- `settings.database.dbname` - database name.
- `settings.database.user` - database user.
- `settings.database.password` - database password.
- `settings.database.pool-size` - set the initial/min/max pool size of database connections.
- `settings.database.stored-requests-query` - the SQL query to fetch stored requests.
- `settings.database.amp-stored-requests-query` - the SQL query to fetch AMP stored requests.

For HTTP data source available next options:
- `settings.http.endpoint` - the url to fetch stored requests.
- `settings.http.amp-endpoint` - the url to fetch AMP stored requests.

For caching available next options:
- `settings.in-memory-cache.ttl-seconds` - how long (in seconds) data will be available in LRU cache.
- `settings.in-memory-cache.cache-size` - the size of LRU cache.
- `settings.in-memory-cache.notification-endpoints-enabled` - if equals to `true` two additional endpoints will be
available: [/storedrequests/openrtb2](endpoints/storedrequests/openrtb2.md) and [/storedrequests/amp](endpoints/storedrequests/amp.md).

## Host Cookie
- `host-cookie.optout-cookie.name` - set the cookie name for optout checking.
- `host-cookie.optout-cookie.value` - set the cookie value for optout checking.
- `host-cookie.opt-out-url` - set the url for user redirect in case of opt out.
- `host-cookie.opt-in-url` - set the url for user redirect in case of opt in.
- `host-cookie.family` - set the family name value for host cookie.
- `host-cookie.cookie-name` - set the name value for host cookie.
- `host-cookie.domain` - set the domain value for host cookie.
- `host-cookie.ttl-days` - set the cookie ttl in days.

## Google Recaptcha
- `recaptcha-url` - the url for Google Recaptcha service to submit user verification.
- `recaptcha-secret` - Google Recaptcha secret string given to certain domain account.

## Server status
- `status-response` - message returned by /status endpoint when server is ready to serve requests.
If not defined in config, endpoint will respond with 'No Content' (204) status with empty body.

## GDPR
- `gdpr.eea-countries` - comma separated list of countries in European Economic Area (EEA).
- `gdpr.default-value` - determines GDPR in scope default value (if no information in request and no geolocation data).
- `gdpr.host-vendor-id` - the organization running a cluster of Prebid Servers.
- `gdpr.vendorlist.http-endpoint-template` - template string for vendor list url, where `{VERSION}` is used as version number placeholder.
- `gdpr.vendorlist.http-default-timeout-ms` - default operation timeout for obtaining new vendor list.
- `gdpr.vendorlist.filesystem-cache-dir` - directory for local storage cache for vendor list. Should be with `WRITE` permissions for user application run from.

## Geo location
- `geolocation.cookie-sync-enabled` - if equals to `true` geo location service will be used in `/setuid` and `/cookie_sync` endpoints handling.
- `geolocation.openrtb2-auctions-enabled` - if equals to `true` geo location service will be used in `/openrtb2/auction` and `/openrtb2/amp` endpoints handling.

## General settings
- `external-url` - the setting stands for external URL prebid server is reachable by, 
for example address of the load-balancer e.g. http://prebid.host.com.
- `default-timeout-ms` - this setting controls default timeout for /auction endpoint.
- `admin.port` - the port to listen on administration requests.
