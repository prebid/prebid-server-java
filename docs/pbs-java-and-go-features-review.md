# Feature Differences Overview

[Detailed Differences Description](differenceBetweenPBSGo-and-Java.md)

 Feature | Java | Go 
| --- | :---: | :---:|
First Party data |+|-
Stored Requests |+|+
Stored Responses |+|-
Currency Conversion** |+|+
Geo location (used for GDPR) |+|-
Circuit Breaker (Http, DB) |+|-
Passing Bidder ext in `imp[...].ext.prebid.bidder` |-|+
Passing `request.ext.prebid.bidders.BIDDER` to corresponding bidder |+|-
Media Type Price Granularity |+|-
Cache only-winning-bids flag |+|-
User ID Module |+|+
Bid Categories |-|+
Apps/Accounts Blacklist  |+|+
Event Notification endpoint |+|-
Video Auction endpoint |-|+
Video Impression Tracking endpoint |+|-
GDPR |+|+
COPPA |+|+
CCPA |+|-
Cooperative Cookie Syncing |+|-
All adapters ported to OpenRTB |+|-
Remote File Downloader |+|-
Bidder Generator |+|-


**
* PBS-Java Currency conversion supports finding intermediate conversion rate;
* PBS-Go Currency Conversion debug endpoint exposes more information, PBS-Java currently provides last updated time only;
