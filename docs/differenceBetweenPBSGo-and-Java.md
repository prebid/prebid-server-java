# Differences Between Prebid Server Go and Java

October 26, 2018

The sister Prebid Server projects are both busy and moving forward at different paces on different features. Sometimes a feature may exist in one implementation
and not the other for an interim period. This page tracks known differences that may persist for longer than a couple of weeks.

## Feature Differences

1) PBS-Java supports Currency conversion. PBS-Go [issue 280](https://github.com/prebid/prebid-server/issues/280). PBS-Java [PR 22](https://github.com/rubicon-project/prebid-server-java/pull/22)
1) PBS-Java supports IP-address lookup in certain scenarios around GDPR. See https://github.com/rubicon-project/prebid-server-java/blob/master/docs/developers/PrebidServerJava_GDPR_Requirements.pdf
1) PBS-Java supports InfluxDB and Graphite, PBS-Go supports InfluxDB and Prometheus as metrics backend.
1) PBS-Java has Circuit Breaker mechanism for database, http and geolocation requests. This can protect the server in scenarios where an external service becomes unavailable.
1) PBS-Java supports `ext.prebid.cache.{bids,vastxml}.ttlseconds` field to pass the TTL to Cache Service 
(per-impression, per-account and per-mediaType options are in progress [PR 154](https://github.com/rubicon-project/prebid-server-java/pull/154)),
 PBS-Go supports per-impression, per-responseBid and per-mediaType cache TTL options. Also PBS-Go use 60-seconds buffer to adjust cache TTL value.
1) PBS-Java supports `ext.prebid.cache.{bids,vastxml}.returnCreative` field to control creative presence in response (`true` by default).
1) PBS-Java supports checking the latest currency rates details, for example update time. This information is exposed via /currency-rates endpoint on admin port.

## Minor differences

- PBS-Java removes null objects or empty strings (e.g. in Go `/auction` response bid object will have field `hb_cache: ""` whereas in Java it will be absent; also `digitrust: null` in PBS Go is not there in PBS Java). PBS-Go [Issue 476](https://github.com/prebid/prebid-server/issues/476)
- Facebook AudienceNetwork adapter has been ported to use OpenRTB directly in PBS-Java. Other adapters will be ported in the near future. PBS-Go [Issue 211](https://github.com/prebid/prebid-server/issues/211)
- Java and Go adapter internal interface returns currency in different ways:
  - in PBS-Go, the adapter sets BidResponse.currency, which is outside of each TypedBid.
  - in PBS-Java, the adapter set BidderBid[N].currency.
- PBS-Go fixed the IndexExchange-vs-IX issue. PBS-Java fix is in progress.
- PBS-Java Rubicon adapter removes `req.cur` from request to XAPI.
- PBS-Go use "60 seconds buffer + {bid,imp,mediaType}TTL" approach to determine caching TTL period.

## GDPR differences
- PBS-Java supports geo location service interface to determine the country for incoming client request (the host company should provide its own implementation).
- Different checking of purpose IDs (1 - `Storage and access of information`, 3 - `Ad selection, delivery, reporting`):
  - for `/auction` endpoint: in PBS-Java - doesn't support GDPR processing.
  - for `/openrtb2/{auction,amp}` endpoint: in PBS-Java - 1 and 3 (for each bidder from request); in PBS-Go - doesn't support GDPR processing.
  - for `/cookie_sync` endpoint: in PBS-Java - doesn't support GDPR processing; in PBS-Go - only 1 checked.
- PBS-Java allows bidder to enforce GDPR processing. This information available in bidder meta info.
