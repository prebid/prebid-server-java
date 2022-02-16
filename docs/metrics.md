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

where `[DATASOURCE]` is a data source name, `DEFAULT_DS` by default.

## General auction metrics
- `APP_REQUESTS` - number of requests received from applications
- `NO_COOKIE_REQUESTS` - number of requests without `uids` cookie or with one that didn't contain at least one live UID
- `REQUEST_TIME` - timer tracking how long did it take for Prebid Server to serve a request
- `IMPS_REQUESTED` - number if impressions requested
- `IMPS_BANNER` - number of banner impressions
- `IMPS_VIDEO` - number of video impressions
- `IMPS_NATIVE` - number of native impressions
- `IMPS_AUDIO` - number of audio impressions
- `REQUESTS.(OK|BADINPUT|ERR|NETWORKERR|BLACKLISTED_ACCOUNT|BLACKLISTED_APP).(OPENRTB2_WEB|OPENRTB2_APP|AMP|legacy)` - number of requests broken down by status and type
- `bidder-cardinality.<cardinality>.REQUESTS` - number of requests targeting `<cardinality>` of bidders
- `CONNECTION_ACCEPT_ERRORS` - number of errors occurred while establishing HTTP connection
- `DB_QUERY_TIME` - timer tracking how long did it take for database client to obtain the result for a query
- `STORED_REQUESTS_FOUND` - number of stored requests that were found
- `STORED_REQUESTS_MISSING` - number of stored requests that were not found by provided stored request IDs
- `STORED_IMPS_FOUND` - number of stored impressions that were found
- `STORED_IMPS_MISSING` - number of stored impressions that were not found by provided stored impression IDs
- `GEOLOCATION_REQUESTS` - number of times geo location lookup was requested
- `GEOLOCATION_SUCCESSFUL` - number of successful geo location lookup responses
- `GEOLOCATION_FAIL` - number of failed geo location lookup responses
- `circuit-breaker.HTTP.named.<host_id>.OPENED` - state of the http client circuit breaker for a particular host: `1` means opened (requested resource is unavailable), `0` - closed
- `circuit.breaker.HTTP.EXISTING` - number of http client circuit breakers existing currently for all hosts
- `circuit-breaker.DB.OPENED` - state of the database circuit breaker: `1` means opened (database is unavailable), `0` - closed
- `circuit-breaker.GEO.OPENED` - state of the geo location circuit breaker: `1` means opened (geo location resource is unavailable), `0` - closed
- `timeout_notification.OK` - number of times bidders were successfully notified about timeouts
- `timeout_notification.FAILED` - number of unsuccessful attempts to notify bidders about timeouts
- `currency-rates.STALE` - a flag indicating if currency rates obtained from external source are fresh (`0`) or stale (`1`)
- `settings.cache.(STORED_REQUEST|AMP_STORED_REQUEST).refresh.(INITIALIZE|UPDATE).DB_QUERY_TIME` - timer tracking how long was settings cache population
- `settings.cache.(STORED_REQUEST|AMP_STORED_REQUEST).refresh.(INITIALIZE|UPDATE).ERR` - number of errors during settings cache population
- `settings.cache.account.(HIT|MISS)` - number of times account was found or was missing in cache

## Auction per-adapter metrics
- `adapter.<bidder-name>.NO_COOKIE_REQUESTS` - number of requests made to `<bidder-name>` that did not contain UID
- `adapter.<bidder-name>.REQUEST_TIME` - timer tracking how long did it take to make a request to `<bidder-name>`
- `adapter.<bidder-name>.PRICES` - histogram of bid prices received from `<bidder-name>`
- `adapter.<bidder-name>.BIDS_RECEIVED` - number of bids received from `<bidder-name>`
- `adapter.<bidder-name>.(BANNER|VIDEO|AUDIO|NATIVE).(ADM_BIDS_RECEIVED|NURL_BIDS_RECEIVED)` - number of bids received from `<bidder-name>` broken down by bid type and whether they had `adm` or `nurl` specified
- `adapter.<bidder-name>.requests.type.(OPENRTB2_WEB|openrtb-app|AMP|legacy)` - number of requests made to `<bidder-name>` broken down by type of incoming request
- `adapter.<bidder-name>.requests.(GOTBIDS|NOBID|BADINPUT|BADSERVERRESPONSE|TIMEOUT|UNKNOWN_ERROR)` - number of requests made to `<bidder-name>` broken down by result status
- `adapter.<bidder-name>.(OPENRTB2_WEB|openrtb-app|AMP|legacy).tcf.USERID_REMOVED` - number of requests made to `<bidder-name>` that required userid removed as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(OPENRTB2_WEB|openrtb-app|AMP|legacy).tcf.GEO_MASKED` - number of requests made to `<bidder-name>` that required geo information removed as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(OPENRTB2_WEB|openrtb-app|AMP|legacy).tcf.REQUEST_BLOCKED` - number of requests made to `<bidder-name>` that were blocked as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.(OPENRTB2_WEB|openrtb-app|AMP|legacy).tcf.ANALYTICS_BLOCKED` - number of requests made to `<bidder-name>` that required analytics blocked as a result of TCF enforcement for that bidder
- `adapter.<bidder-name>.response.validation.size.(WARN|ERR)` - number of banner bids received from the `<bidder-name>` that had invalid size
- `adapter.<bidder-name>.response.validation.secure.(WARN|ERR)` - number of bids received from the `<bidder-name>` that had insecure creative while in secure context

## Auction per-account metrics
Following metrics are collected and submitted if account is configured with `basic` verbosity:   
- `account.<account-id>.requests` - number of requests received from account with `<account-id>`
- `account.<account-id>.response.validation.size.(WARN|ERR)` - number of banner bids received from account with `<account-id>` that had invalid size
- `account.<account-id>.response.validation.secure.(WARN|ERR)` - number of bids received from account with `<account-id>` that had insecure creative while in secure context

Following metrics are collected and submitted if account is configured with `detailed` verbosity:
- `account.<account-id>.requests.type.(OPENRTB2_WEB,openrtb-app,AMP,legacy)` - number of requests received from account with `<account-id>` broken down by type of incoming request
- `account.<account-id>.requests.REJECTED` - number of rejected requests caused by incorrect `accountId`
- `account.<account-id>.adapter.<bidder-name>.REQUEST_TIME` - timer tracking how long did it take to make a request to `<bidder-name>` when incoming request was from `<account-id>` 
- `account.<account-id>.adapter.<bidder-name>.BIDS_RECEIVED` - number of bids received from `<bidder-name>` when incoming request was from `<account-id>`
- `account.<account-id>.adapter.<bidder-name>.requests.(GOTBIDS|NOBID)` - number of requests made to `<bidder-name>` broken down by result status  when incoming request was from `<account-id>`

## General Prebid Cache metrics
- `prebid_cache.requests.OK` - timer tracking how long did successful cache requests take
- `prebid_cache.requests.ERR` - timer tracking how long did failed cache requests take
- `prebid_cache.creative_size.<creative_type>` - histogram tracking creative sizes for specific type

## Prebid Cache per-account metrics
- `account.<account-id>.prebid_cache.requests.OK` - timer tracking how long did successful cache requests take when incoming request was from `<account-id>`
- `account.<account-id>.prebid_cache.requests.ERR` - timer tracking how long did failed cache requests take when incoming request was from `<account-id>`
- `account.<account-id>.prebid_cache.creative_size.<creative_type>` - histogram tracking creative sizes for specific type when incoming request was from `<account-id>`

## /cookie_sync endpoint metrics
- `COOKIE_SYNC_REQUESTS` - number of requests received
- `cookie_sync.<bidder-name>.GEN` - number of times cookies was synced per bidder 
- `cookie_sync.<bidder-name>.MATCHES` - number of times cookie was already matched when synced per bidder 
- `cookie_sync.<bidder-name>.tcf.BLOCKED` - number of times cookie sync was prevented by TCF per bidder

## /setuid endpoint metrics
- `usersync.OPT_OUTS` - number of requests received with `uids` cookie containing `optout=true`
- `usersync.BAD_REQUESTS` - number of requests received with bidder not specified
- `usersync.<bidder-name>.SETS` - number of requests received resulted in `uid` cookie update for `<bidder-name>`
- `usersync.<bidder-name>.tcf.BLOCKED` - number of requests received that didn't result in `uid` cookie update for `<bidder-name>` because of lack of user consent for this action according to TCF
- `usersync.<bidder-name>.tcf.INVALID` - number of requests received that are lacking of a valid consent string for `<bidder-name>` in setuid endpoint
- `usersync.all.tcf.INVALID` - number of requests received that are lacking of a valid consent string for all requested bidders cookieSync endpoint

## Privacy metrics
- `privacy.tcf.(MISSING|INVALID)` - number of requests lacking a valid consent string
- `privacy.tcf.(v1,v2).REQUEST` - number of requests by TCF version
- `privacy.tcf.(v1,v2).UNKNOWN_GEO` - number of requests received from unknown geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).IN_GEO` - number of requests received from TCF-concerned geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).OUT_GEO` - number of requests received outside of TCF-concerned geo region with consent string of particular version
- `privacy.tcf.(v1,v2).vendorlist.(MISSING|OK|ERR|FALLBACK)` - number of processed vendor lists of particular version
- `privacy.usp.SPECIFIED` - number of requests with a valid US Privacy string (CCPA)
- `privacy.usp.OPT_OUT` - number of requests that required privacy enforcement according to CCPA rules
- `privacy.LMT` - number of requests that required privacy enforcement according to LMT flag
- `privacy.COPPA` - number of requests that required privacy enforcement according to COPPA rules

## Analytics metrics
- `analytics.<reporter-name>.(EVENT_AUCTION|EVENT_AMP|EVENT_VIDEO|EVENT_COOKIE_SYNC|EVENT_NOTIFICATION|EVENT_SETUID).OK` - number of succeeded processed event requests
- `analytics.<reporter-name>.(EVENT_AUCTION|EVENT_AMP|EVENT_VIDEO|EVENT_COOKIE_SYNC|EVENT_NOTIFICATION|EVENT_SETUID).TIMEOUT` - number of event requests, failed with timeout cause
- `analytics.<reporter-name>.(EVENT_AUCTION|EVENT_AMP|EVENT_VIDEO|EVENT_COOKIE_SYNC|EVENT_NOTIFICATION|EVENT_SETUID).ERR` - number of event requests, failed with errors
- `analytics.<reporter-name>.(EVENT_AUCTION|EVENT_AMP|EVENT_VIDEO|EVENT_COOKIE_SYNC|EVENT_NOTIFICATION|EVENT_SETUID).BADINPUT` - number of event requests, rejected with bad input cause

## win notifications
- `WIN_NOTIFICATIONS` - total number of win notifications.
- `WIN_REQUESTS` - total number of requests sent to user service for win notifications.
- `WIN_REQUEST_PREPARATION_FAILED` - number of request failed validation and were not sent.
- `WIN_REQUEST_TIME` - latency between request to user service and response for win notifications.
- `WIN_REQUEST_FAILED` - number of failed request sent to user service for win notifications.
- `WIN_REQUEST_SUCCESSFUL` - number of successful request sent to user service for win notifications.

## user details
- `USER_DETAILS_REQUESTS` - total number of requests sent to user service to get user details.
- `USER_DETAILS_REQUEST_PREPARATION_FAILED` - number of request failed validation and were not sent.
- `USER_DETAILS_REQUEST_TIME` - latency between request to user service and response to get user details.
- `USER_DETAILS_REQUEST_FAILED` - number of failed request sent to user service to get user details.
- `USER_DETAILS_REQUEST_SUCCESSFUL` -  number of successful request sent to user service to get user details.

## Programmatic guaranteed metrics
- `pg.PLANNER_LINEITEMS_RECEIVED` - number of line items received from general planner.
- `pg.PLANNER_REQUESTS` - total number of requests sent to general planner.
- `pg.PLANNER_REQUEST_FAILED` - number of failed request sent to general planner.
- `pg.PLANNER_REQUEST_SUCCESSFUL` - number of successful requests sent to general planner.
- `pg.PLANNER_REQUEST_TIME` - latency between request to general planner and its successful (200 OK) response.
- `pg.DELIVERY_REQUESTS` - total number of requests to delivery stats service.
- `pg.DELIVERY_REQUEST_FAILED` - number of failed requests to delivery stats service.
- `pg.DELIVERY_REQUEST_SUCCESSFUL` - number of successful requests to delivery stats service.
- `pg.DELIVERY_REQUEST_TIME` - latency between request to delivery stats and its successful (200 OK) response.
