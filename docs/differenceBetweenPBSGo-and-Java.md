# Differences Between Prebid Server Go and Java

April 23, 2018

The sister Prebid Server projects are both busy and moving forward at different paces on different features. Sometimes a feature may exist in one implementation
and not the other for an interim period. This page tracks known differences that may persist for longer than a couple of weeks.

## Feature Differences

1) PBS-Java supports Custom Price Granularity feature. PBS-Go [issue 323](https://github.com/prebid/prebid-server/issues/323). PBS-Java [commit](https://github.com/rubicon-project/prebid-server-java/commit/73b6d4c1e3899df5d3b4202cf21e46d783523e88)
1) The audienceNetwork adapter in PBS-Java has been converted to use OpenRTB natively. (other adapters underway)
1) PBS-Java supports Currency conversion. PBS-Go [issue 280](https://github.com/prebid/prebid-server/issues/280). PBS-Java [PR 22](https://github.com/rubicon-project/prebid-server-java/pull/22)

## Minor differences

- PBS-Java removes null objects or empty strings (e.g. in Go `/auction` response bid object will have field `hb_cache: ""` whereas in Java it will be absent; also `digitrust: null` in PBS Go is not there in PBS Java). PBS-Go [Issue 476](https://github.com/prebid/prebid-server/issues/476)
