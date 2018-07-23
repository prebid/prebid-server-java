# Differences Between Prebid Server Go and Java

July 23, 2018

The sister Prebid Server projects are both busy and moving forward at different paces on different features. Sometimes a feature may exist in one implementation
and not the other for an interim period. This page tracks known differences that may persist for longer than a couple of weeks.

## Feature Differences

1) The audienceNetwork adapter in PBS-Java has been converted to use OpenRTB natively. (other adapters underway)
1) PBS-Java supports Currency conversion. PBS-Go [issue 280](https://github.com/prebid/prebid-server/issues/280). PBS-Java [PR 22](https://github.com/rubicon-project/prebid-server-java/pull/22)
1) PBS-Java supports IP-address lookup in certain scenarios around GDPR. See https://github.com/rubicon-project/prebid-server-java/blob/master/docs/developers/PrebidServerJava_GDPR_Requirements.pdf

## Minor differences

- PBS-Java removes null objects or empty strings (e.g. in Go `/auction` response bid object will have field `hb_cache: ""` whereas in Java it will be absent; also `digitrust: null` in PBS Go is not there in PBS Java). PBS-Go [Issue 476](https://github.com/prebid/prebid-server/issues/476)
- Facebook AudienceNetwork adapter has been ported to use OpenRTB directly in PBS-Java. Other adapters will be ported in the near future. PBS-Go [Issue 211](https://github.com/prebid/prebid-server/issues/211)
- Java and Go adapters return currency in different ways:
  - in PBS-Go, the adapter sets BidResponse.currency, which is outside of each TypedBid.
  - in PBS-Java, they set BidderBid[N].currency
