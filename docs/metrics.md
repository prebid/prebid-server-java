# Full list of application metrics

This document describes all metrics collected and submitted to configured backends by the Prebid Server.

## System metrics
- `vertx.http.servers.[IP]:[PORT].open-netsockets.count` - current number of open connections

where:
- `[IP]` should be equal to IP address of bound network interface on cluster node for Prebid Server (for example: `0.0.0.0`)
- `[PORT]` should be equal to `http.port` configuration property

Other available metrics can found at [Vert.x Dropwizard Metrics](https://vertx.io/docs/vertx-dropwizard-metrics/java/#_the_metrics) page.

## General auction metrics
- `app_requests` - number of requests received from applications
- `no_cookie_requests` - number of requests without `uids` cookie or with one that didn't contain at least one live UID
- `safari_requests` - number of requests received from Safari browser
- `safari_no_cookie_requests` - number of requests received from Safari browser without `uids` cookie or with one that didn't contain at least one live UID
- `request_time` - timer tracking how long did it take for Prebid Server to serve a request
- `imps_requested` - number if impressions requested
- `imps_banner` - number of banner impressions
- `imps_video` - number of video impressions
- `imps_native` - number of native impressions
- `imps_audio` - number of audio impressions
- `requests.(ok|badinput|err|networkerr|blacklisted_account|blacklisted_app).(openrtb2-web|openrtb-app|amp|legacy)` - number of requests broken down by status and type
- `connection_accept_errors` - number of errors occurred while establishing HTTP connection
- `db_circuitbreaker_opened` - number of times database circuit breaker was opened (database is unavailable)
- `db_circuitbreaker_closed` - number of times database circuit breaker was closed (database is available again)
- `db_query_time` - timer tracking how long did it take for database client to obtain the result for a query
- `httpclient_circuitbreaker_opened.<underscored_host>` - number of times http client circuit breaker was opened (requested resource is unavailable) for particular host
- `httpclient_circuitbreaker_closed.<underscored_host>` - number of times http client circuit breaker was closed (requested resource is available again) for particular host
- `stored_requests_found` - number of stored requests that were found
- `stored_requests_missing` - number of stored requests that were not found by provided stored request IDs
- `stored_imps_found` - number of stored impressions that were found
- `stored_imps_missing` - number of stored impressions that were not found by provided stored impression IDs
- `geolocation_requests` - number of times geo location lookup was requested
- `geolocation_successful` - number of successful geo location lookup responses
- `geolocation_fail` - number of failed geo location lookup responses
- `geolocation_circuitbreaker_opened` - number of times geo location circuit breaker was opened (geo location resource is unavailable)
- `geolocation_circuitbreaker_closed` - number of times geo location circuit breaker was closed (geo location resource is available again)
- `prebid_cache_request_success_time` - timer tracking how long did successful cache request take
- `prebid_cache_request_error_time` - timer tracking how long did failed cache request take

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

## Auction per-account metrics
Following metrics are collected and submitted if account is configured with `basic` verbosity:   
- `account.<account-id>.requests` - number of requests received from account with `<account-id>`

Following metrics are collected and submitted if account is configured with `detailed` verbosity:
- `account.<account-id>.requests.type.(openrtb2-web,openrtb-app,amp,legacy)` - number of requests received from account with `<account-id>` broken down by type of incoming request
- `account.<account-id>.<bidder-name>.request_time` - timer tracking how long did it take to make a request to `<bidder-name>` when incoming request was from `<account-id>` 
- `account.<account-id>.<bidder-name>.bids_received` - number of bids received from `<bidder-name>` when incoming request was from `<account-id>`
- `account.<account-id>.<bidder-name>.requests.(gotbids|nobid)` - number of requests made to `<bidder-name>` broken down by result status  when incoming request was from `<account-id>`
- `account.<account-id>.requests.rejected` - number of rejected requests caused by incorrect `accountId` ([UnauthorizedAccountException.java](https://github.com/rubicon-project/prebid-server-java/blob/master/src/main/java/org/prebid/server/exception/UnauthorizedAccountException.java))

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

## Privacy metrics
- `privacy.tcf.(missing|invalid)` - number of requests lacking a valid consent string
- `privacy.tcf.(v1,v2).unknown-geo` - number of requests received from unknown geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).in-geo` - number of requests received from TCF-concerned geo region with consent string of particular version 
- `privacy.tcf.(v1,v2).out-geo` - number of requests received outside of TCF-concerned geo region with consent string of particular version
- `privacy.tcf.(v1,v2).vendorlist.(missing|ok|err)` - number of processed vendor lists of particular version
- `privacy.usp.specified` - number of requests with a valid US Privacy string (CCPA)
- `privacy.usp.opt-out` - number of requests that required privacy enforcement according to CCPA rules
