# Feature Differences Overview

[Detailed Differences Description](differenceBetweenPBSGo-and-Java.md)

 Feature | Java | Go 
| --- | :---: | :---:|
GDPR TCF1.1 |+|+
GDPR TCF2 |+|+
Geo location (used for GDPR) |+|-
COPPA |+|+
CCPA |+|+
AMP |+|+
Stored Requests |+|+
Stored Responses |+|-
PBJS First Party data |+|-
Currency Conversion** |+|+
Multiple root schains |+|-
Price Granularity |+|+
Price Granularity per MediaType|+|-
User ID Module support |+|+
Account exclude list  |+|+
Event Notification endpoint |+|-
Video ad support |+|+
Long-form video endpoint |-|+
IAB advertiser category mapping |-|+
Aliases |+|+
Video Impression Tracking endpoint |+|-
Cooperative Cookie Syncing |+|-
Circuit Breaker (Http, DB) |+|-
Operational metrics |+|+
Supports both "debug" and "test" flags |+|-
All adapters ported to OpenRTB |+|-
Echo stored request video attributes in response |+|-
Accept account ID on AMP requests |+|-
Cache only-winning-bids flag |+|-
Remote File Downloader |+|-
Bidder Generator |+|-
Passing `request.ext.prebid.bidders.BIDDER` to corresponding bidder |+|-

**
* PBS-Java Currency conversion supports finding intermediate conversion rate;
* PBS-Go Currency Conversion debug endpoint exposes more information, PBS-Java currently provides last updated time only;
