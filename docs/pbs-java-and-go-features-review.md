# Feature Differences Overview

[Detailed Differences Description](differenceBetweenPBSGo-and-Java.md)

 Feature | Java | Go 
| --- | :---: | :---:|
First Party data |+|-
Stored Responses |+|-
Currency Conversion** |+|+
Geo location (used for GDPR) |+|-
Circuit Breaker (Http, DB) |+|-
Passing Bidder ext in `imp[...].ext.prebid.bidder` |-|+
Media Type Price Granularity |+|-
Cache only-winning-bids Flag |+|-
User ID module |+|+
Bid Categories |-|+
Event endpoint |+|-
Video Endpoint |-|+
COPPA |+|+
Video Impression Tracking |+|-
All adapters ported to OpenRTB |+|-
Bidder Generator |+|-


**
* PBS-Go Currency Conversion is disabled by default and not finalized yet (issue still open);
* PBS-Java Currency conversion supports finding intermediate conversion rate;
* PBS-Go Currency Conversion debug endpoint exposes more information, PBS-Java currently provides last updated time only;