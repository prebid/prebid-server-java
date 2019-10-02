# Full list of application configuration options

This document describes all configuration properties available for Prebid Server.

## Spring
- `spring.main.banner-mode` - determine if the banner has to be printed on System.out (console), using the configured logger (log) or not at all (off).

This section can be extended against standard [Spring configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-spring-application.html) options.

## Vert.x
- `vertx.worker-pool-size` - set the maximum number of worker threads to be used by the Vert.x instance.
- `vertx.uploads-dir` - directory that Vert.x [BodyHandler](http://vertx.io/docs/apidocs/io/vertx/ext/web/handler/BodyHandler.html) will use to store multi-part file uploads. 
This parameter exists to allow to change the location of the directory Vert.x will create because it will and there is no way to make it not.
- `vertx.http-server-instances` - how many http server instances should be created. 
This parameter affects how many CPU cores will be utilized by the application. Rough assumption - one http server instance will keep 1 CPU core busy.
- `vertx.init-timeout-ms` - time to wait for asynchronous initialization steps completion before considering them stuck. When exceeded - exception is thrown and Prebid Server stops.

## HTTP
- `http.port` - the port to listen on.
- `http.max-headers-size` - set the maximum length of all headers.
- `http.ssl` - enable SSL/TLS support.
- `http.jks-path` - path to the java keystore (if ssl is enabled).
- `http.jks-password` - password for the keystore (if ssl is enabled).

## HTTP Client
- `http-client.max-pool-size` - set the maximum pool size for outgoing connections.
- `http-client.connect-timeout-ms` - set the connect timeout.
- `http-client.circuit-breaker.enabled` - if equals to `true` circuit breaker will be used to make http client more robust.
- `http-client.circuit-breaker.opening-threshold` - the number of failure before opening the circuit.
- `http-client.circuit-breaker.opening-interval-ms` - time interval for opening the circuit breaker if failures count reached.
- `http-client.circuit-breaker.closing-interval-ms` - time spent in open state before attempting to re-try.
- `http-client.use-compression` - if equals to `true` httpclient compression is enabled for requests (see [also](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html#setTryUseCompression-boolean-))
- `http-client.max-redirects` - set the maximum amount of HTTP redirections to follow. A value of 0 (the default) prevents redirections from being followed.

## Auction (OpenRTB)
- `auction.blacklisted-accounts` - comma separated list of blacklisted account IDs.
- `auction.default-timeout-ms` - default operation timeout for OpenRTB Auction requests.
- `auction.max-timeout-ms` - maximum operation timeout for OpenRTB Auction requests.
- `auction.timeout-adjustment-ms` - reduces timeout value passed in Auction request so that Prebid Server can handle timeouts from adapters and respond to the request before it times out.
- `auction.max-request-size` - set the maximum size in bytes of OpenRTB Auction request.
- `auction.stored-requests-timeout-ms` - timeout for stored requests fetching.
- `auction.ad-server-currency` - default currency for auction, if its value was not specified in request. Important note: PBS uses ISO-4217 codes for the representation of currencies.
- `auction.cache.expected-request-time-ms` - approximate value in milliseconds for Cache Service interacting. This time will be subtracted from global timeout.

## Amp (OpenRTB)
- `amp.default-timeout-ms` - default operation timeout for OpenRTB Amp requests.
- `amp.max-timeout-ms` - maximum operation timeout for OpenRTB Amp requests.
- `amp.timeout-adjustment-ms` - reduces timeout value passed in Amp request so that Prebid Server can handle timeouts from adapters and respond to the AMP RTC request before it times out.
- `amp.custom-targeting` - a list of bidders whose custom targeting should be included in AMP responses.

## Setuid
- `setuid.default-timeout-ms` - default operation timeout for requests to `/setuid` endpoint.

## Cookie Sync
- `cookie-sync.default-timeout-ms` - default operation timeout for requests to `/cookie_sync` endpoint.
- `cookie-sync.coop-sync.default` - default value for coopSync when it missing in requests to `/cookie_sync` endpoint.
- `cookie-sync.coop-sync.pri` - lists of bidders prioritised in groups.

## Adapters
- `adapters.*` - the section for bidder specific configuration options.

There are several typical keys:
- `adapters.<BIDDER_NAME>.enabled` - indicates the bidder should be active and ready for auction. By default all bidders are disabled.
- `adapters.<BIDDER_NAME>.endpoint` - the url for submitting bids.
- `adapters.<BIDDER_NAME>.pbs-enforces-gdpr` - indicates if pbs server provides gdpr support for bidder or bidder will handle it itself.
- `adapters.<BIDDER_NAME>.deprecated-names` - comma separated deprecated names of bidder.
- `adapters.<BIDDER_NAME>.aliases` - comma separated aliases of bidder.
- `adapters.<BIDDER_NAME>.usersync.url` - the url for synchronizing UIDs cookie.
- `adapters.<BIDDER_NAME>.usersync.redirect-url` - the redirect part of url for synchronizing UIDs cookie.
- `adapters.<BIDDER_NAME>.usersync.cookie-family-name` - the family name by which user ids within adapter's realm are stored in uidsCookie.
- `adapters.<BIDDER_NAME>.usersync.type` - usersync type (i.e. redirect, iframe).
- `adapters.<BIDDER_NAME>.usersync.support-cors` - flag signals if CORS supported by usersync.

But feel free to add additional bidder's specific options.

## Currency Converter
- `currency-converter.enabled` - if equals to `true` the currency conversion service will be enabled to fetch updated rates and convert bid currencies. Also enables `/currency-rates` endpoint on admin port.
- `currency-converter.url` - the url for Prebid.org’s currency file. [More details](http://prebid.org/dev-docs/modules/currency.html)
- `currency-converter.default-timeout-ms` - default operation timeout for fetching currency rates.
- `currency-converter.refresh-period-ms` - default refresh period for currency rates updates.

## Metrics
- `metrics.metricType` - set the type of metric counter for [Dropwizard Metrics](http://metrics.dropwizard.io). Can be `flushingCounter` (default), `counter` or `meter`.

So far metrics cannot be submitted simultaneously to many backends. Currently we support `graphite` and `influxdb`. 
Also, for debug purposes you can use `console` as metrics backend.

For `graphite` backend type available next options:
- `metrics.graphite.enabled` - if equals to `true` then `graphite` will be used to submit metrics.
- `metrics.graphite.prefix` - the prefix of all metric names.
- `metrics.graphite.host` - the graphite host for sending statistics.
- `metrics.graphite.port` - the graphite port for sending statistics.
- `metrics.graphite.interval` - interval in seconds between successive sending metrics.

For `influxdb` backend type available next options:
- `metrics.influxdb.enabled` - if equals to `true` then `influxdb` will be used to submit metrics.
- `metrics.influxdb.prefix` - the prefix of all metric names.
- `metrics.influxdb.protocol` - external service destination protocol.
- `metrics.influxdb.host` - the influxDb host for sending metrics.
- `metrics.influxdb.port` - the influxDb port for sending metrics.
- `metrics.influxdb.database` - the influxDb database to write metrics.
- `metrics.influxdb.auth` - the authorization string to be used to connect to InfluxDb, of format `username:password`.
- `metrics.influxdb.connectTimeout` - the connect timeout.
- `metrics.influxdb.readTimeout` - the response timeout.
- `metrics.influxdb.interval` - interval in seconds between successive sending metrics.

For `console` backend type available next options:
- `metrics.console.enabled` - if equals to `true` then `console` will be used to submit metrics.
- `metrics.console.interval` - interval in seconds between successive sending metrics.

It is possible to define how many account-level metrics will be submitted on per-account basis.
See [metrics documentation](metrics.md) for complete list of metrics submitted at each verbosity level.
- `metrics.accounts.default-verbosity` - verbosity for accounts not specified in next sections. Allowed values: `none, basic, detailed`. Default is `none`.
- `metrics.accounts.basic-verbosity` - a list of accounts for which only basic metrics will be submitted.
- `metrics.accounts.detailed-verbosity` - a list of accounts for which all metrics will be submitted. 

## Cache
- `cache.scheme` - set the external Cache Service protocol: `http`, `https`, etc.
- `cache.host` - set the external Cache Service destination in format `host:port`.
- `cache.path` - set the external Cache Service path, for example `/cache`.
- `cache.query` - appends to the cache path as query string params (used for legacy Auction requests).
- `cache.banner-ttl-seconds` - how long (in seconds) banner will be available via the external Cache Service.
- `cache.video-ttl-seconds` - how long (in seconds) video creative will be available via the external Cache Service.
- `cache.account.<ACCOUNT>.banner-ttl-seconds` - how long (in seconds) banner will be available in Cache Service 
for particular publisher account. Overrides `cache.banner-ttl-seconds` property.
- `cache.account.<ACCOUNT>.video-ttl-seconds` - how long (in seconds) video creative will be available in Cache Service 
for particular publisher account. Overrides `cache.video-ttl-seconds` property.

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
- `settings.database.stored-responses-query` - the SQL query to fetch stored responses.
- `settings.database.circuit-breaker.enabled` - if equals to `true` circuit breaker will be used to make database client more robust.
- `settings.database.circuit-breaker.opening-threshold` - the number of failure before opening the circuit.
- `settings.database.circuit-breaker.opening-interval-ms` - time interval for opening the circuit breaker if failures count reached.
- `settings.database.circuit-breaker.closing-interval-ms` - time spent in open state before attempting to re-try.

For HTTP data source available next options:
- `settings.http.endpoint` - the url to fetch stored requests.
- `settings.http.amp-endpoint` - the url to fetch AMP stored requests.

For account processing rules available next options:
- `settings.enforce-valid-account` - if equals to `true` then request without account id will be rejected with 401.

For caching available next options:
- `settings.in-memory-cache.ttl-seconds` - how long (in seconds) data will be available in LRU cache.
- `settings.in-memory-cache.cache-size` - the size of LRU cache.
- `settings.in-memory-cache.notification-endpoints-enabled` - if equals to `true` two additional endpoints will be
available: [/storedrequests/openrtb2](endpoints/storedrequests/openrtb2.md) and [/storedrequests/amp](endpoints/storedrequests/amp.md).
- `settings.in-memory-cache.http-update.endpoint` - the url to fetch stored request updates.
- `settings.in-memory-cache.http-update.amp-endpoint` - the url to fetch AMP stored request updates.
- `settings.in-memory-cache.http-update.refresh-rate` - refresh period in ms for stored request updates.
- `settings.in-memory-cache.http-update.timeout` - timeout for obtaining stored request updates.
- `settings.in-memory-cache.jdbc-update.init-query` - initial query for fetching all stored requests at the startup.
- `settings.in-memory-cache.jdbc-update.update-query` - a query for periodical update of stored requests, that should
contain 'WHERE last_updated > ?' to fetch only the records that were updated since previous check.
- `settings.in-memory-cache.jdbc-update.amp-init-query` - initial query for fetching all AMP stored requests at the startup.
- `settings.in-memory-cache.jdbc-update.amp-update-query` - a query for periodical update of AMP stored requests, that should
contain 'WHERE last_updated > ?' to fetch only the records that were updated since previous check.
- `settings.in-memory-cache.jdbc-update.refresh-rate` - refresh period in ms for stored request updates.
- `settings.in-memory-cache.jdbc-update.timeout` - timeout for obtaining stored request updates.

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
- `status-response` - message returned by ApplicationChecker in /status endpoint when server is ready to serve requests.
If not defined in config all other Health Checkers would be disabled and endpoint will respond with 'No Content' (204) status with empty body.

## Health Check
- `health-check.database.enabled` - if equals to `true` the database health check will be enabled to periodically check database status.
- `health-check.database.refresh-period-ms` - the refresh period for database status updates.

## GDPR
- `gdpr.eea-countries` - comma separated list of countries in European Economic Area (EEA).
- `gdpr.default-value` - determines GDPR in scope default value (if no information in request and no geolocation data).
- `gdpr.host-vendor-id` - the organization running a cluster of Prebid Servers.
- `gdpr.vendorlist.http-endpoint-template` - template string for vendor list url, where `{VERSION}` is used as version number placeholder.
- `gdpr.vendorlist.http-default-timeout-ms` - default operation timeout for obtaining new vendor list.
- `gdpr.vendorlist.filesystem-cache-dir` - directory for local storage cache for vendor list. Should be with `WRITE` permissions for user application run from.
- `gdpr.geolocation.enabled` - if equals to `true` the geo location service will be used to determine the country for client request.

## Auction (Legacy)
- `default-timeout-ms` - this setting controls default timeout for /auction endpoint.
- `max-timeout-ms` - this setting controls maximum timeout for /auction endpoint.
- `timeout-adjustment-ms` - reduces timeout value passed in legacy Auction request so that Prebid Server can handle timeouts from adapters and respond to the request before it times out.

## General settings
- `external-url` - the setting stands for external URL prebid server is reachable by, for example address of the load-balancer e.g. http://prebid.host.com.
- `admin.port` - the port to listen on administration requests.
