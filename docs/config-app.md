
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
- `vertx.enable-per-client-endpoint-metrics` - enables HTTP client metrics per destination endpoint (`host:port`)

## HTTP
- `http.port` - the port to listen on.
- `http.max-headers-size` - set the maximum length of all headers.
- `http.ssl` - enable SSL/TLS support.
- `http.jks-path` - path to the java keystore (if ssl is enabled).
- `http.jks-password` - password for the keystore (if ssl is enabled).

## HTTP Client
- `http-client.max-pool-size` - set the maximum pool size for outgoing connections (per host).
- `http-client.idle-timeout-ms` - set the maximum time idle connections could exist before being reaped
- `http-client.pool-cleaner-period-ms` - set how often idle connections will be closed removed from pool
- `http-client.connect-timeout-ms` - set the connect timeout.
- `http-client.circuit-breaker.enabled` - if equals to `true` circuit breaker will be used to make http client more robust.
- `http-client.circuit-breaker.opening-threshold` - the number of failures before opening the circuit.
- `http-client.circuit-breaker.opening-interval-ms` - time interval for opening the circuit breaker if failures count reached.
- `http-client.circuit-breaker.closing-interval-ms` - time spent in open state before attempting to re-try.
- `http-client.use-compression` - if equals to `true` httpclient compression is enabled for requests (see [also](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpClientOptions.html#setTryUseCompression-boolean-))
- `http-client.max-redirects` - set the maximum amount of HTTP redirections to follow. A value of 0 (the default) prevents redirections from being followed.
- `http-client.ssl` - enable SSL/TLS support.
- `http-client.jks-path` - path to the java keystore (if ssl is enabled).
- `http-client.jks-password` - password for the keystore (if ssl is enabled).

## Remote-file-syncer
Remote File Syncer can be related to particular entity like geolocation maxmind service etc.
Removes and downloads file again if depending service cant process probably corrupted file in the first start.

- `<SERVICE>.remote-file-syncer.download-url` - url to database file to download.
- `<SERVICE>.remote-file-syncer.save-filepath` - full path to the usable file, which will be consumed by internal service.
- `<SERVICE>.remote-file-syncer.tmp-filepath` - full path to the temporary file.
- `<SERVICE>.remote-file-syncer.retry-count` - how many times try to download.
- `<SERVICE>.remote-file-syncer.retry-interval-ms` - how long to wait between failed retries.
- `<SERVICE>.remote-file-syncer.timeout-ms` - default operation timeout for obtaining database file.
- `<SERVICE>.remote-file-syncer.update-interval-ms` - time interval between updates of the usable file.
- `<SERVICE>.remote-file-syncer.http-client.connect-timeout-ms` - set the connect timeout.
- `<SERVICE>.remote-file-syncer.http-client.max-redirects` - set the maximum amount of HTTP redirections to follow. A value of 0 (the default) prevents redirections from being followed.

## General settings
- `host-id` - the  ID of node where prebid server deployed.
- `external-url` - the setting stands for external URL prebid server is reachable by, for example address of the load-balancer e.g. http://prebid.host.com.
- `admin.port` - the port to listen on administration requests.

## Auction (Legacy)
- `default-timeout-ms` - this setting controls default timeout for /auction endpoint.
- `max-timeout-ms` - this setting controls maximum timeout for /auction endpoint.
- `timeout-adjustment-ms` - reduces timeout value passed in legacy Auction request so that Prebid Server can handle timeouts from adapters and respond to the request before it times out.

## Default bid request
- `default-request.file.path` - path to a JSON file containing the default request

## Auction (OpenRTB)
- `auction.blacklisted-accounts` - comma separated list of blacklisted account IDs.
- `auction.blacklisted-apps` - comma separated list of blacklisted applications IDs, requests from which should not be processed.
- `auction.default-timeout-ms` - default operation timeout for OpenRTB Auction requests.
- `auction.max-timeout-ms` - maximum operation timeout for OpenRTB Auction requests.
- `auction.timeout-adjustment-ms` - reduces timeout value passed in Auction request so that Prebid Server can handle timeouts from adapters and respond to the request before it times out.
- `auction.max-request-size` - set the maximum size in bytes of OpenRTB Auction request.
- `auction.stored-requests-timeout-ms` - timeout for stored requests fetching.
- `auction.ad-server-currency` - default currency for auction, if its value was not specified in request. Important note: PBS uses ISO-4217 codes for the representation of currencies.
- `auction.cache.expected-request-time-ms` - approximate value in milliseconds for Cache Service interacting. This time will be subtracted from global timeout.
- `auction.cache.only-winning-bids` - if equals to `true` only the winning bids would be cached. Has lower priority than request-specific flags.
- `auction.generate-bid-id` - whether to generate seatbid[].bid[].ext.prebid.bidid in the OpenRTB response.
- `auction.generate-source-tid` - whether to generate bidrequest.source.tid in the OpenRTB request.
- `auction.validations.banner-creative-max-size` - enables creative max size validation for banners. Possible values: `skip`, `enforce`, `warn`. Default is `skip`.
- `auction.validations.secure-markup` - enables secure markup validation. Possible values: `skip`, `enforce`, `warn`. Default is `skip`.
- `auction.host-schain-node` - defines global schain node that will be appended to `request.source.ext.schain.nodes` passed to bidders

## Amp (OpenRTB)
- `amp.default-timeout-ms` - default operation timeout for OpenRTB Amp requests.
- `amp.max-timeout-ms` - maximum operation timeout for OpenRTB Amp requests.
- `amp.timeout-adjustment-ms` - reduces timeout value passed in Amp request so that Prebid Server can handle timeouts from adapters and respond to the AMP RTC request before it times out.
- `amp.custom-targeting` - a list of bidders whose custom targeting should be included in AMP responses.

## Timeout notification
- `auction.timeout-notification.timeout-ms` - HTTP timeout to use when sending notifications about bidder timeouts
- `auction.timeout-notification.log-result` - causes bidder timeout notification result to be logged
- `auction.timeout-notification.log-failure-only` - causes only bidder timeout notification failures to be logged
- `auction.timeout-notification.log-sampling-rate` - instructs apply sampling when logging bidder timeout notification results

## Video
- `auction.video.stored-required` - flag forces to merge with stored request
- `auction.blacklisted-accounts` - comma separated list of blacklisted account IDs.
- `video.stored-requests-timeout-ms` - timeout for stored requests fetching.
- `auction.ad-server-currency` - default currency for video auction, if its value was not specified in request. Important note: PBS uses ISO-4217 codes for the representation of currencies.

## Setuid
- `setuid.default-timeout-ms` - default operation timeout for requests to `/setuid` endpoint.

## Cookie Sync
- `cookie-sync.default-timeout-ms` - default operation timeout for requests to `/cookie_sync` endpoint.
- `cookie-sync.coop-sync.default` - default value for coopSync when it missing in requests to `/cookie_sync` endpoint.
- `cookie-sync.coop-sync.pri` - lists of bidders prioritised in groups.

## Vtrack
- `vtrack.allow-unknown-bidder` - flag that allows servicing requests with bidders who were not configured in Prebid Server.
- `vtrack.modify-vast-for-unknown-bidder` - flag that allows modifying the VAST value and adding the impression tag to it, for bidders who were not configured in Prebid Server.

## Adapters
- `adapters.*` - the section for bidder specific configuration options.

There are several typical keys:
- `adapters.<BIDDER_NAME>.enabled` - indicates the bidder should be active and ready for auction. By default all bidders are disabled.
- `adapters.<BIDDER_NAME>.endpoint` - the url for submitting bids.
- `adapters.<BIDDER_NAME>.pbs-enforces-gdpr` - indicates if PBS server provides GDPR support for bidder or bidder will handle it itself.
- `adapters.<BIDDER_NAME>.pbs-enforces-ccpa` - indicates if PBS server provides CCPA support for bidder or bidder will handle it itself.
- `adapters.<BIDDER_NAME>.modifying-vast-xml-allowed` - indicates if PBS server is allowed to modify VAST creatives received from this bidder.
- `adapters.<BIDDER_NAME>.deprecated-names` - comma separated deprecated names of bidder.
- `adapters.<BIDDER_NAME>.meta-info.maintainer-email` - specifies maintainer e-mail address that will be shown in bidder info endpoint response.
- `adapters.<BIDDER_NAME>.meta-info.app-media-types` - specifies media types supported for app requests that will be shown in bidder info endpoint response.
- `adapters.<BIDDER_NAME>.meta-info.site-media-types` - specifies media types supported for site requests that will be shown in bidder info endpoint response.
- `adapters.<BIDDER_NAME>.meta-info.supported-vendors` - specifies viewability vendors supported by the bidder.
- `adapters.<BIDDER_NAME>.meta-info.vendor-id` - specifies TCF vendor ID.
- `adapters.<BIDDER_NAME>.usersync.url` - the url for synchronizing UIDs cookie.
- `adapters.<BIDDER_NAME>.usersync.redirect-url` - the redirect part of url for synchronizing UIDs cookie.
- `adapters.<BIDDER_NAME>.usersync.cookie-family-name` - the family name by which user ids within adapter's realm are stored in uidsCookie.
- `adapters.<BIDDER_NAME>.usersync.type` - usersync type (i.e. redirect, iframe).
- `adapters.<BIDDER_NAME>.usersync.support-cors` - flag signals if CORS supported by usersync.

In addition, each bidder could have arbitrary aliases configured that will look and act very much the same as the bidder itself.
Aliases are configured by adding child configuration object at `adapters.<BIDDER_NAME>.aliases.<BIDDER_ALIAS>.`, aliases 
support the same configuration options that their bidder counterparts support except `aliases` (i.e. it's not possible 
to declare alias of an alias). Another restriction of aliases configuration is that they cannot declare support for media types 
not supported by their bidders (however aliases could narrow down media types they support). For example: if the bidder 
is written to not support native site requests, then an alias cannot magically decide to change that; however, if a bidder 
supports native site requests, and the alias does not want to for some reason, it has the ability to remove that support.

Also, each bidder could have its own bidder-specific options.

## Logging
- `logging.http-interaction.max-limit` - maximum value for the number of interactions to log in one take.

## Logging
- `logging.change-level.max-duration-ms` - maximum duration (in milliseconds) for which logging level could be changed.

## Currency Converter
- `currency-converter.external-rates.enabled` - if equals to `true` the currency conversion service will be enabled to fetch updated rates and convert bid currencies from external source. Also enables `/currency-rates` endpoint on admin port.
- `currency-converter.external-rates.url` - the url for Prebid.org’s currency file. [More details](http://prebid.org/dev-docs/modules/currency.html)
- `currency-converter.external-rates.default-timeout-ms` - default operation timeout for fetching currency rates.
- `currency-converter.external-rates.refresh-period-ms` - default refresh period for currency rates updates.
- `currency-converter.external-rates.stale-after-ms` - how old currency rates should be to become considered stale.
- `currency-converter.external-rates.stale-period-ms` - stale period after which the latest external currency rates get discarded.

## Admin Endpoints
- `admin-endpoints.version.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.version.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.version.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.version.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.currency-rates.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.currency-rates.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.currency-rates.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.currency-rates.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.storedrequest.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.storedrequest.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.storedrequest.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.storedrequest.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.storedrequest-amp.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.storedrequest-amp.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.storedrequest-amp.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.storedrequest-amp.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.cache-invalidation.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.cache-invalidation.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.cache-invalidation.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.cache-invalidation.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.logging-httpinteraction.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.logging-httpinteraction.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.logging-httpinteraction.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.logging-httpinteraction.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.tracelog.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.tracelog.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.tracelog.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.tracelog.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.deals-status.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.deals-status.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.deals-status.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.deals-status.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.lineitem-status.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.lineitem-status.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.lineitem-status.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.lineitem-status.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.e2eadmin.enabled` - if equals to `true` the endpoint will be available.
- `admin-endpoints.e2eadmin.path` - the server context path where the endpoint will be accessible.
- `admin-endpoints.e2eadmin.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
- `admin-endpoints.e2eadmin.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

- `admin-endpoints.credentials` - user and password for access to admin endpoints if `admin-endpoints.[NAME].protected` is true`.

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
- `metrics.influxdb.tags` - the influxDb tags, optional key-value metrics metadata.

For `console` backend type available next options:
- `metrics.console.enabled` - if equals to `true` then `console` will be used to submit metrics.
- `metrics.console.interval` - interval in seconds between successive sending metrics.

For `prometheus` backend type available next options:
- `metrics.prometheus.port` - if a port is specified a prometheus reporter will start on that port 

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
- `settings.database.account-query` - the SQL query to fetch account.
- `settings.database.stored-requests-query` - the SQL query to fetch stored requests.
- `settings.database.amp-stored-requests-query` - the SQL query to fetch AMP stored requests.
- `settings.database.stored-responses-query` - the SQL query to fetch stored responses.
- `settings.database.circuit-breaker.enabled` - if equals to `true` circuit breaker will be used to make database client more robust.
- `settings.database.circuit-breaker.opening-threshold` - the number of failures before opening the circuit.
- `settings.database.circuit-breaker.opening-interval-ms` - time interval for opening the circuit breaker if failures count reached.
- `settings.database.circuit-breaker.closing-interval-ms` - time spent in open state before attempting to re-try.

For HTTP data source available next options:
- `settings.http.endpoint` - the url to fetch stored requests.
- `settings.http.amp-endpoint` - the url to fetch AMP stored requests.
- `settings.http.video-endpoint` - the url to fetch video stored requests.

For account processing rules available next options:
- `settings.enforce-valid-account` - if equals to `true` then request without account id will be rejected with 401.
- `settings.generate-storedrequest-bidrequest-id` - overrides `bidrequest.id` in amp or app stored request with generated UUID if true. Default value is false. This flag can be overridden by setting `bidrequest.id` as `{{UUID}}` placeholder directly in stored request.

It is possible to specify default account configuration values that will be assumed if account config have them 
unspecified or missing at all. Example:
```yaml
settings:  
  default-account-config: >
    {
      "auction": {
        "default-integration": "pbjs"
        "events": {
          "enabled": true
        }
      },
      "privacy": {
        "enforce-ccpa": true,
        "gdpr": {
          "enabled": true
        }
      }
    }
```
See [application settings](application-settings.md) for full reference of available configuration parameters.

For caching available next options:
- `settings.in-memory-cache.ttl-seconds` - how long (in seconds) data will be available in LRU cache.
- `settings.in-memory-cache.cache-size` - the size of LRU cache.
- `settings.in-memory-cache.notification-endpoints-enabled` - if equals to `true` two additional endpoints will be
available: [/storedrequests/openrtb2](endpoints/storedrequests/openrtb2.md) and [/storedrequests/amp](endpoints/storedrequests/amp.md).
- `settings.in-memory-cache.account-invalidation-enabled` - if equals to `true` additional admin protected endpoints will be
available: `/cache/invalidate?account={accountId}` which remove account from the cache.
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

For targeting available next options:
- `settings.targeting.truncate-attr-chars` - set the max length for names of targeting keywords (0 means no truncation).

## Host Cookie
- `host-cookie.optout-cookie.name` - set the cookie name for optout checking.
- `host-cookie.optout-cookie.value` - set the cookie value for optout checking.
- `host-cookie.opt-out-url` - set the url for user redirect in case of opt out.
- `host-cookie.opt-in-url` - set the url for user redirect in case of opt in.
- `host-cookie.family` - set the family name value for host cookie.
- `host-cookie.cookie-name` - set the name value for host cookie.
- `host-cookie.domain` - set the domain value for host cookie.
- `host-cookie.ttl-days` - set the cookie ttl in days.
- `host-cookie.max-cookie-size-bytes` - a size limit for UIDs Cookie. Valid values are `0` (disabled) and `>500`.

## Google Recaptcha
- `recaptcha-url` - the url for Google Recaptcha service to submit user verification.
- `recaptcha-secret` - Google Recaptcha secret string given to certain domain account.

## Server status
- `status-response` - message returned by ApplicationChecker in /status endpoint when server is ready to serve requests.
If not defined in config all other Health Checkers would be disabled and endpoint will respond with 'No Content' (204) status with empty body.

## Health Check
- `health-check.database.enabled` - if equals to `true` the database health check will be enabled to periodically check database status.
- `health-check.database.refresh-period-ms` - the refresh period for database status updates.
- `health-check.geolocation.enabled` - if equals to `true` the geolocation service health check will be enabled to periodically check the status.
- `health-check.geolocation.refresh-period-ms` - the refresh period for geolocation service status updates.

## GDPR
- `gdpr.eea-countries` - comma separated list of countries in European Economic Area (EEA).
- `gdpr.default-value` - determines GDPR in scope default value (if no information in request and no geolocation data).
- `gdpr.host-vendor-id` - the organization running a cluster of Prebid Servers.
- `gdpr.enabled` - gdpr feature switch. Default `true`.
- `gdpr.purposes.pN.enforce-purpose` - define type of enforcement confirmation: `no`/`basic`/`full`. Default `full`
- `gdpr.purposes.pN.enforce-vendors` - if equals to `true`, user must give consent to use vendors. Purposes will be omitted. Default `true`
- `gdpr.purposes.pN.vendor-exceptions[]` - bidder names that will be treated opposite to `pN.enforce-vendors` value.
- `gdpr.special-features.sfN.enforce` - if equals to `true`, special feature will be enforced for purpose. Default `true`
- `gdpr.special-features.sfN.vendor-exceptions[]` - bidder names that will be treated opposite to `sfN.enforce` value.
- `gdpr.purpose-one-treatment-interpretation` - option that allows to skip the Purpose one enforcement workflow.
- `gdpr.vendorlist.default-timeout-ms` - default operation timeout for obtaining new vendor list.
- `gdpr.vendorlist.v2.http-endpoint-template` - template string for vendor list url version 2.
- `gdpr.vendorlist.v2.refresh-missing-list-period-ms` - time to wait between attempts to fetch vendor list version that previously was reported to be missing by origin. Default `3600000` (one hour).
- `gdpr.vendorlist.v2.fallback-vendor-list-path` - location on the file system of the fallback vendor list that will be used in place of missing vendor list versions. Optional.
- `gdpr.vendorlist.v2.deprecated` - Flag to show is this vendor list is deprecated or not.
- `gdpr.vendorlist.v2.cache-dir` - directory for local storage cache for vendor list. Should be with `WRITE` permissions for user application run from.

## CCPA
- `ccpa.enforce` - if equals to `true` enforces to check ccpa policy, otherwise ignore ccpa verification.

## LMT
- `lmt.enforce` - if equals to `true` enforces to check lmt policy, otherwise ignore lmt verification.

## Geo Location
- `geolocation.enabled` - if equals to `true` the geo location service will be used to determine the country for client request.
- `geolocation.circuit-breaker.enabled` - if equals to `true` circuit breaker will be used to make geo location client more robust.
- `geolocation.circuit-breaker.opening-threshold` - the number of failures before opening the circuit.
- `geolocation.circuit-breaker.opening-interval-ms` - time interval for opening the circuit breaker if failures count reached.
- `geolocation.circuit-breaker.closing-interval-ms` - time spent in open state before attempting to re-try.
- `geolocation.type` - set the geo location service provider, can be `maxmind` or custom provided by hosting company.
- `geolocation.maxmind` - section for [MaxMind](https://www.maxmind.com) configuration as geo location service provider.
- `geolocation.maxmind.remote-file-syncer` - use RemoteFileSyncer component for downloading/updating MaxMind database file. See [RemoteFileSyncer](#remote-file-syncer) section for its configuration.

## Analytics
- `analytics.pubstack.enabled` - if equals to `true` the Pubstack analytics module will be enabled. Default value is `false`. 
- `analytics.pubstack.endpoint` - url for reporting events and fetching configuration. 
- `analytics.pubstack.scopeid` - defined the scope provided by the Pubstack Support Team.
- `analytics.pubstack.configuration-refresh-delay-ms` - delay in milliseconds between remote config updates.
- `analytics.pubstack.timeout-ms` - timeout in milliseconds for report and fetch config requests.
- `analytics.pubstack.buffers.size-bytes` - threshold in bytes for buffer to send events. 
- `analytics.pubstack.buffers.count` - threshold in events count for buffer to send events
- `analytics.pubstack.buffers.report-ttl-ms` - max period between two reports.

## Programmatic Guaranteed Delivery
- `deals.planner.plan-endpoint` - planner endpoint to get plans from.
- `deals.planner.update-period` - cron expression to start job for requesting Line Item metadata updates from the Planner.
- `deals.planner.plan-advance-period` - cron expression to start job for advancing Line Items to the next plan.
- `deals.planner.retry-period-sec` - how long (in seconds) to wait before re-sending a request to the Planner that previously failed with 5xx HTTP error code.
- `deals.planner.timeout-ms` - default operation timeout for requests to planner's endpoints.
- `deals.planner.register-endpoint` - register endpoint to get plans from.
- `deals.planner.register-period-sec` - time period (in seconds) to send register request to the Planner.
- `deals.planner.username` - username for planner BasicAuth.
- `deals.planner.password` - password for planner BasicAuth.
- `deals.delivery-stats.delivery-period` - cron expression to start job for sending delivery progress to planner.
- `deals.delivery-stats.cached-reports-number` - how many reports to cache while planner is unresponsive.
- `deals.delivery-stats.timeout-ms` - default operation timeout for requests to delivery progress endpoints.
- `deals.delivery-stats.username` - username for delivery progress BasicAuth.
- `deals.delivery-stats.password` - password for delivery progress BasicAuth.
- `deals.delivery-stats.line-items-per-report` - max number of line items in each report to split for batching. Default is 25.
- `deals.delivery-stats.reports-interval-ms` - interval in ms between consecutive reports. Default is 0.
- `deals.delivery-stats.batches-interval-ms` - interval in ms between consecutive batches. Default is 1000.
- `deals.delivery-stats.request-compression-enabled` - enables request gzip compression when set to true.
- `deals.delivery-progress.line-item-status-ttl-sec` - how long to store line item's metrics after it was expired.
- `deals.delivery-progress.cached-plans-number` -  how many plans to store in metrics per line item.
- `deals.delivery-progress.report-reset-period`- cron expression to start job for closing current delivery progress and starting new one.
- `deals.delivery-progress-report.competitors-number`- number of line items top competitors to send in delivery progress report.
- `deals.user-data.user-details-endpoint` - user Data Store endpoint to get user details from.
- `deals.user-data.win-event-endpoint` - user Data Store endpoint to which win events should be sent.
- `deals.user-data.timeout` - time to wait (in milliseconds) for User Data Service response.
- `deals.user-data.user-ids` - list of Rules for determining user identifiers to send to User Data Store.
- `deals.max-deals-per-bidder` - maximum number of deals to send to each bidder.
- `deals.alert-proxy.enabled` - enable alert proxy service if `true`.
- `deals.alert-proxy.url` - alert service endpoint to send alerts to.
- `deals.alert-proxy.timeout-sec` - default operation timeout for requests to alert service endpoint.
- `deals.alert-proxy.username` - username for alert proxy BasicAuth.
- `deals.alert-proxy.password` - password for alert proxy BasicAuth.
- `deals.alert-proxy.alert-types` - key value pair of alert type and sampling factor to send high priority alert.
