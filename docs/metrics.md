# Full list of application metrics

This document describes all metrics collected and submitted to configured backends by the Prebid Server.

## System metrics
Other available metrics not mentioned here can found at 
[Vert.x Dropwizard Metrics](https://vertx.io/docs/vertx-dropwizard-metrics/java/#_the_metrics) page.

### HTTP server metrics
- `vertx.http.servers.[IP]:[PORT].open-netsockets.count` - current number of open connections

where:
- `[IP]` should be equal to IP address of bound network interface on cluster node for Prebid Server (for example: `0.0.0.0`)
- `[PORT]` should be equal to `http.port` configuration property

### HTTP client metrics
- `vertx.http.clients.connections.{min,max,mean,p95,p99}` - how long connections live
- `vertx.http.clients.connections.{m1_rate,m5_rate,m15_rate,mean_rate}` - rate of the connection occurrences
- `vertx.http.clients.requests.{min,max,mean,p95,p99}` - request time
- `vertx.http.clients.requests.{m1_rate,m5_rate,m15_rate,mean_rate}` - request rate

If HTTP client per destination endpoint metrics enabled:
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].queue-delay.{min,max,mean,p95,p99}` - wait time of a pending request in the queue
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].queue-size.count` - actual queue size
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].open-netsockets.count` - actual number of open sockets to the endpoint
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].usage.{min,max,mean,p95,p99}` - time of the delay between the request starts and the response ends
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].in-use` - actual number of in-flight requests
- `vertx.http.clients.endpoint.[ENDPOINT]:[PORT].ttfb` - wait time between the request ended and its response begins

### Database pool metrics
- `vertx.pools.datasouce.[DATASOURCE].queue-delay.{min,max,mean,p95,p99}` - duration of the delay to obtain the resource, i.e the wait time in the queue
- `vertx.pools.datasouce.[DATASOURCE].queue-size.counter` - the actual number of waiters in the queue
- `vertx.pools.datasouce.[DATASOURCE].usage.{min,max,mean,p95,p99}` - duration of the usage of the resource
- `vertx.pools.datasouce.[DATASOURCE].in-use.counter` - actual number of resources used

where `[DATASOURCE]` is a data source name, `DEFAULT_DS` by defaul.

## General auction metrics
- `app_requests` - number of requests received from applications
- `no_cookie_requests` - number of requests without `uids` cookie or with one that didn't contain at least one live UID
- `request_time` - timer tracking how long did it take for Prebid Server to serve a request
- `imps_requested` - number if impressions requested
- `imps_banner` - number of banner impressions
- `imps_video` - number of video impressions
- `imps_native` - number of native impressions
- `imps_audio` - number of audio impressions
- `requests.(ok|badinput|err|networkerr|blacklisted_account|blacklisted_app).(openrtb2-web|openrtb-app|amp|legacy)` - number of requests broken down by status and type
- `bidder-cardinality.<cardinality>.requests` - number of requests targeting `<cardinality>` of bidders
- `connection_accept_errors` - number of errors occurred while establishing HTTP connection
- `db_query_time` - timer tracking how long did it take for database client to obtain the result for a query
- `stored_requests_found` - number of stored requests that were found
- `stored_requests_missing` - number of stored requests that were not found by provided stored request IDs
- `stored_imps_found` - number of stored impressions that were found
- `stored_imps_missing` - number of stored impressions that were not found by provided stored impression IDs
- `geolocation_requests` - number of times geo location lookup was requested
- `geolocation_successful` - number of successful geo location lookup responses
- `geolocation_fail` - number of failed geo location lookup responses
- `circuit-breaker.http.named.<host_id>.opened` - state of the http client circuit breaker for a particular host: `1` means opened (requested resource is unavailable), `0` - closed
- `circuit.breaker.http.existing` - number of http client circuit breakers existing currently for all hosts
- `circuit-breaker.db.opened` - state of the database circuit breaker: `1` means opened (database is unavailable), `0` - closed
- `circuit-breaker.geo.opened` - state of the geo location circuit breaker: `1` means opened (geo location resource is unavailable), `0` - closed
- `timeout_notification.ok` - number of times bidders were successfully notified about timeouts
- `timeout_notification.failed` - number of unsuccessful attempts to notify bidders about timeouts
- `currency-rates.stale` - a flag indicating if currency rates obtained from external source are fresh (`0`) or stale (`1`)
- `settings.cache.(stored-request|amp-stored-request).refresh.(initialize|update).db_query_time` - timer tracking how long was settings cache population
- `settings.cache.(stored-request|amp-stored-request).refresh.(initialize|update).err` - number of errors during settings cache population
- `settings.cache.account.(hit|miss)` - number of times account was found or was missing in cache

## Auction per-adapter metrics
- `adapter.<bidder-name>.no_cookie_requests` - number of requests made to `<bidder-name>` that did not contain UID
- `adapter.<bidder-name>.request_time` - timer tracking how long did it take to make a request to `<bidder-name>`
- `adapter.<bidder-name>.prices` - histogram of bid prices received from `<bidder-name>`
- `adapter.<bidder-name>.bids_received` - number of bids received from `<bidder-name>`
- `adapter.<bidder-name>.(banner|video|audio|native).(adm_bids_received|nurl_bids_received)` - number of bids received from `<bidder-name>` broken down by bid type and whether they had `adm` or `nurl` specified
- `adapter.<bidder-name>.requests.type.(openrtb2-web|openrtb-app|amp|legacy)` - number of requests made to `<bidder-name>` broken down by type of incoming request
- `adapter.<bidder-name>.requests.(gotbids|nobid|badinput|badserverresponse|timeout|unknown_error)` - number of requests made to `<bidder-name>` broken down by result status
- `adapter.<bidder-name>.(openrtb2-web|openrtb-app|amp|legacy).tcf.userid_removed` - number of requests made to `<bidder-name>` that required userid removed as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(openrtb2-web|openrtb-app|amp|legacy).tcf.geo_masked` - number of requests made to `<bidder-name>` that required geo information removed as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(openrtb2-web|openrtb-app|amp|legacy).tcf.request_blocked` - number of requests made to `<bidder-name>` that were blocked as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(openrtb2-web|openrtb-app|amp|legacy).tcf.analytics_blocked` - number of requests made to `<bidder-name>` that required analytics blocked as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.response.validation.size.(warn|err)` - number of banner bids received from the `<bidder-name>` that had invalid size
- `adapter.<bidder-name>.response.validation.secure.(warn|err)` - number of bids received from the `<bidder-name>` that had insecure creative while in secure context

## Auction per-account metrics
Following metrics are collected and submitted if account is configured with `basic` verbosity:   
- `account.<account-id>.requests` - number of requests received from account with `<account-id>`
- `account.<account-id>.response.validation.size.(warn|err)` - number of banner bids received from account with `<account-id>` that had invalid size
- `account.<account-id>.response.validation.secure.(warn|err)` - number of bids received from account with `<account-id>` that had insecure creative while in secure context

Following metrics are collected and submitted if account is configured with `detailed` verbosity:
- `account.<account-id>.requests.type.(openrtb2-web,openrtb-app,amp,legacy)` - number of requests received from account with `<account-id>` broken down by type of incoming request
- `account.<account-id>.requests.rejected` - number of rejected requests caused by incorrect `accountId`
- `account.<account-id>.adapter.<bidder-name>.request_time` - timer tracking how long did it take to make a request to `<bidder-name>` when incoming request was from `<account-id>` 
- `account.<account-id>.adapter.<bidder-name>.bids_received` - number of bids received from `<bidder-name>` when incoming request was from `<account-id>`
- `account.<account-id>.adapter.<bidder-name>.requests.(gotbids|nobid)` - number of requests made to `<bidder-name>` broken down by result status  when incoming request was from `<account-id>`

## General Prebid Cache metrics
- `prebid_cache.requests.ok` - timer tracking how long did successful cache requests take
- `prebid_cache.requests.err` - timer tracking how long did failed cache requests take
- `prebid_cache.creative_size.<creative_type>` - histogram tracking creative sizes for specific type

## Prebid Cache per-account metrics
- `account.<account-id>.prebid_cache.requests.ok` - timer tracking how long did successful cache requests take when incoming request was from `<account-id>`
- `account.<account-id>.prebid_cache.requests.err` - timer tracking how long did failed cache requests take when incoming request was from `<account-id>`
- `account.<account-id>.prebid_cache.creative_size.<creative_type>` - histogram tracking creative sizes for specific type when incoming request was from `<account-id>`

## /cookie_sync endpoint metrics
- `cookie_sync_requests` - number of requests received
- `cookie_sync.<bidder-name>.gen` - number of times cookies was synced per bidder 
- `cookie_sync.<bidder-name>.matches` - number of times cookie was already matched when synced per bidder 
- `cookie_sync.<bidder-name>.tcf.blocked` - number of times cookie sync was prevented by TCF per bidder

## /setuid endpoint metrics
- `usersync.opt_outs` - number of requests received with `uids` cookie containing `optout=true`
- `usersync.bad_requests` - number of requests received with bidder not specified
- `usersync.<bidder-name>.sets` - number of requests received resulted in `uid` cookie update for `<bidder-name>`
- `usersync.<bidder-name>.tcf.blocked` - number of requests received that didn't result in `uid` cookie update for `<bidder-name>` because of lack of user consent for this action according to TCF
- `usersync.<bidder-name>.tcf.invalid` - number of requests received that are lacking of a valid consent string for `<bidder-name>` in setuid endpoint
- `usersync.all.tcf.invalid` - number of requests received that are lacking of a valid consent string for all requested bidders cookieSync endpoint

## Privacy metrics
- `privacy.tcf.(missing|invalid)` - number of requests lacking a valid consent string
- `privacy.tcf.(v1,v2).requests` - number of requests by TCF version
- `privacy.tcf.(v1,v2).unknown-geo` - number of requests received from unknown geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).in-geo` - number of requests received from TCF-concerned geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).out-geo` - number of requests received outside of TCF-concerned geo region with consent string of particular version
- `privacy.tcf.(v1,v2).vendorlist.(missing|ok|err|fallback)` - number of processed vendor lists of particular version
- `privacy.usp.specified` - number of requests with a valid US Privacy string (CCPA)
- `privacy.usp.opt-out` - number of requests that required privacy enforcement according to CCPA rules
- `privacy.lmt` - number of requests that required privacy enforcement according to LMT flag
- `privacy.coppa` - number of requests that required privacy enforcement according to COPPA rules

## Analytics metrics
- `analytics.<reporter-name>.(auction|amp|video|cookie_sync|event|setuid).ok` - number of succeeded processed event requests
- `analytics.<reporter-name>.(auction|amp|video|cookie_sync|event|setuid).timeout` - number of event requests, failed with timeout cause
- `analytics.<reporter-name>.(auction|amp|video|cookie_sync|event|setuid).err` - number of event requests, failed with errors
- `analytics.<reporter-name>.(auction|amp|video|cookie_sync|event|setuid).badinput` - number of event requests, rejected with bad input cause

## win notifications
- `win_notifications` - total number of win notifications.
- `win_requests` - total number of requests sent to user service for win notifications.
- `win_request_preparation_failed` - number of request failed validation and were not sent.
- `win_request_time` - latency between request to user service and response for win notifications.
- `win_request_failed` - number of failed request sent to user service for win notifications.
- `win_request_successful` - number of successful request sent to user service for win notifications.

## user details
- `user_details_requests` - total number of requests sent to user service to get user details.
- `user_details_request_preparation_failed` - number of request failed validation and were not sent.
- `user_details_request_time` - latency between request to user service and response to get user details.
- `user_details_request_failed` - number of failed request sent to user service to get user details.
- `user_details_request_successful` -  number of successful request sent to user service to get user details.

## Programmatic guaranteed metrics
- `pg.planner_lineitems_received` - number of line items received from general planner.
- `pg.planner_requests` - total number of requests sent to general planner.
- `pg.planner_request_failed` - number of failed request sent to general planner.
- `pg.planner_request_successful` - number of successful requests sent to general planner.
- `pg.planner_request_time` - latency between request to general planner and its successful (200 OK) response.
- `pg.delivery_requests` - total number of requests to delivery stats service.
- `pg.delivery_request_failed` - number of failed requests to delivery stats service.
- `pg.delivery_request_successful` - number of successful requests to delivery stats service.
- `pg.delivery_request_time` - latency between request to delivery stats and its successful (200 OK) response.
