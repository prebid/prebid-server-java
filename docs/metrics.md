# Full list of application metrics

This document describes all metrics collected and submitted to configured backends by the Prebid Server.

## General auction metrics
- `app_requests` - number of requests received from applications
- `no_cookie_requests` - number of requests without `uids` cookie or with one that didn't contain at least one live UID
- `safari_requests` - number of requests received from Safari browser
- `safari_no_cookie_requests` - number of requests received from Safari browser without `uids` cookie or with one that didn't contain at least one live UID
- `request_time` - timer tracking how long did it take for Prebid Server to serve a request
- `imps_requested` - number if impressions requested
- `requests.(ok|badinput|err).(openrtb2-web|openrtb-app|amp|legacy)` - number of requests broken down by status and type

## Auction per-adapter metrics
- `adapter.<bidder-name>.no_cookie_requests` - number of requests made to `<bidder-name>` that did not contain UID
- `adapter.<bidder-name>.request_time` - timer tracking how long did it take to make a request to `<bidder-name>`
- `adapter.<bidder-name>.prices` - histogram of bid prices received from `<bidder-name>`
- `adapter.<bidder-name>.bids_received` - number of bids received from `<bidder-name>`
- `adapter.<bidder-name>.(banner|video|audio|native).(adm_bids_received|nurl_bids_received)` - number of bids received from `<bidder-name>` broken down by bid type and whether they had `adm` or `nurl` specified.
- `adapter.<bidder-name>.requests.type.(openrtb2-web|openrtb-app|amp|legacy)` - number of requests made to `<bidder-name>` broken down by type of incoming request
- `adapter.<bidder-name>.requests.(gotbids|nobid|badinput|badserverresponse|timeout|unknown_error)` - number of requests made to `<bidder-name>` broken down by result status
- `adapter.<bidder-name>.gdpr_masked` - number of requests made to `<bidder-name>` that required personal information masking as a result of GDPR enforcement for that bidder

## Auction per-account metrics
Following metrics are collected and submitted if account is configured with `basic` verbosity:   
- `account.<account-id>.requests` - number of requests received from account with `<account-id>`

Following metrics are collected and submitted if account is configured with `detailed` verbosity:
- `account.<account-id>.requests.type.(openrtb2-web,openrtb-app,amp,legacy)` - number of requests received from account with `<account-id>` broken down by type of incoming request
- `account.<account-id>.<bidder-name>.request_time` - timer tracking how long did it take to make a request to `<bidder-name>` when incoming request was from `<account-id>` 
- `account.<account-id>.<bidder-name>.bids_received` - number of bids received from `<bidder-name>` when incoming request was from `<account-id>`
- `account.<account-id>.<bidder-name>.requests.(gotbids|nobid)` - number of requests made to `<bidder-name>` broken down by result status  when incoming request was from `<account-id>`

## /cookie_sync endpoint metrics
- `cookie_sync_requests` - number of requests received

## /setuid endpoint metrics
- `usersync.opt_outs` - number of requests received with `uids` cookie containing `optout=true`
- `usersync.bad_requests` - number of requests received with bidder not specified
- `usersync.<bidder-name>.sets` - number of requests received resulted in `uid` cookie update for `<bidder-name>`
- `usersync.<bidder-name>.gdpr_prevent` - number of requests received that didn't result in `uid` cookie update for `<bidder-name>` because of lack of user consent for this action according to GDPR
