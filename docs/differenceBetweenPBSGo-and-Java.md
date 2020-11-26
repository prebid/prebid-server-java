# Differences Between Prebid Server Go and Java

January 24, 2019

The sister Prebid Server projects are both busy and moving forward at different paces on different features. Sometimes a feature may exist in one implementation
and not the other for an interim period. This page tracks known differences that may persist for longer than a couple of weeks.

[Feature Checklist Overview](pbs-java-and-go-features-review.md)

## Feature Differences

1) PBS-Java supports Stored Responses [issue 861](https://github.com/prebid/prebid-server/issues/861). PBS-Java [PR 354](https://github.com/prebid/prebid-server-java/pull/354).
1) PBS-Java supports Currency conversion. PBS-Go has it implemented, but disabled by default(still under dev) [issue 280](https://github.com/prebid/prebid-server/issues/280), [issue 760](https://github.com/prebid/prebid-server/pull/760). PBS-Java [PR 22](https://github.com/prebid/prebid-server-java/pull/22)
1) PBS-Java Currency conversion supports finding intermediate conversion rate, e.g. if pairs USD : AUD = 1.2 and EUR : AUD = 1.5 are present and EUR to USD conversion is needed, will return (1/1.5) * 1.2 conversion rate.
1) PBS-Go Currency conversion admin debug endpoint exposes following information: Sync source URL, Internal rates, Update frequency, Last update. PBS-Java `/currency-rates` admin endpoint currently supports checking the latest update time only and is not available if currency conversion is disabled.
1) PBS-Java supports IP-address lookup in certain scenarios around GDPR. See https://github.com/prebid/prebid-server-java/blob/master/docs/developers/PrebidServerJava_GDPR_Requirements.pdf
1) PBS-Java supports InfluxDB, Graphite and Prometheus, PBS-Go supports InfluxDB and Prometheus as metrics backend.
1) PBS-Java has Circuit Breaker mechanism for database, http and geolocation requests. This can protect the server in scenarios where an external service becomes unavailable.
1) PBS-Java supports `ext.prebid.cache.{bids,vastxml}.returnCreative` field to control creative presence in response (`true` by default).
1) PBS-Java support caching winning bids only through `auction.cache.only-winning-bids` configuration property or request field `request.ext.prebid.cache.winningonly`. PBS-Java [issue 279](https://github.com/prebid/prebid-server-java/issues/279), [PR 484](https://github.com/prebid/prebid-server-java/pull/484).
1) PBS-Java has a specific `host-cookie` and `uids` cookie processing for all endpoints, that sets `uids.HOST-BIDDER` from `host-cookie` if first is absent or not equal to second.
1) PBS-Java has a specific `/cookie-sync` behaviour, that sets `/setuid` as usersync-url for host-bidder if `host-cookie` specified but `uids.HOST-BIDDER` undefined or differs.
1) PBS-Java has `/event` endpoint to allow Web browsers and mobile applications to notify about different ad events (win, view etc). Filling new bid extensions `response.seatbid.bid.ext.prebid.events.{win,view}` with events url after successful auction completing makes it possible.
1) PBS-Java supports per-account cache TTL and event URLs configuration in the database in columns `banner_cache_ttl`, `video_cache_ttl` and `events_enabled`.
1) PBS-Java does not support passing bidder extensions in `imp[...].ext.prebid.bidder`. PBS-Go [PR 846](https://github.com/prebid/prebid-server/pull/846)
1) PBS-Java responds with active bidders only in `/info/bidders` and `/info/bidders/all` endpoints, although PBS-Go returns all implemented ones. Calling `/info/bidders/{bidderName}` with a disabled bidder name will result in 404 Not Found, which is a desired behaviour, unlike in PBS-Go [issue 988](https://github.com/prebid/prebid-server/issues/988), [PR](https://github.com/prebid/prebid-server/pull/989).
1) PBS-Java supports video impression tracking [issue 1015](https://github.com/prebid/prebid-server/issues/1015). PBS-Java [PR 437](https://github.com/prebid/prebid-server-java/pull/437). 

## Minor differences

- PBS-Java removes null objects or empty strings (e.g. in Go `/auction` response bid object will have field `hb_cache: ""` whereas in Java it will be absent; also `digitrust: null` in PBS Go is not there in PBS Java). PBS-Go [Issue 476](https://github.com/prebid/prebid-server/issues/476)
- All adapters have been ported to use OpenRTB directly in PBS-Java. PBS-Go Facebook AudienceNetwork adapter [Issue 211](https://github.com/prebid/prebid-server/issues/211)
- Java and Go adapter internal interface returns currency in different ways:
  - in PBS-Go, the adapter sets BidResponse.currency, which is outside of each TypedBid.
  - in PBS-Java, the adapter set BidderBid[N].currency.
- PBS-Go use "60 seconds buffer + {bid,imp,mediaType}TTL" approach to determine caching TTL period.
- PBS-Java has different names for system metrics. For example instead of `active_connections` it uses `vertx.http.servers.[IP]:[PORT].open-netsockets.count`. See [Metrics](metrics.md) for details.
- PBS-Go `/openrtb2/{auction,amp}` returns HTTP 503 Service Unavailable if request has blacklisted account or app, PBS-Java returns HTTP 403 Forbidden.

## GDPR differences
- PBS-Java supports geo location service interface to determine the country for incoming client request and provides a default implementation using MaxMind GeoLite2 Country database.
- Different checking of purpose IDs (1 - `Storage and access of information`, 3 - `Ad selection, delivery, reporting`):
  - for `/auction` endpoint: in PBS-Java - doesn't support GDPR processing.
  - for `/openrtb2/{auction,amp}` endpoint: in PBS-Java - 1 and 3 (for each bidder from request); in PBS-Go - doesn't support GDPR processing.
  - for `/cookie_sync` endpoint: in PBS-Java - doesn't support GDPR processing; in PBS-Go - only 1 checked.
- PBS-Java allows bidder to enforce GDPR processing. This information available in bidder meta info.
