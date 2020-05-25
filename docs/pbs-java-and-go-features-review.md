# Feature Differences Overview

[Detailed Differences Description](differenceBetweenPBSGo-and-Java.md)

 Feature | Java | Go 
| --- | :---: | :---:|
First Party Data |+|-
Stored Requests |+|+
Stored Responses |+|-
Currency Conversion** |+|+
Geo Location (used for GDPR) |+|-
Circuit Breaker (HTTP, DB) |+|-
Passing Bidder extension in `imp[i].ext.prebid.bidder` |+|+
Passing `ext.prebid.bidders.BIDDER` to corresponding bidder |+|-
Media Type Price Granularity |+|-
Cache only-winning-bids flag |+|-
User ID Module |+|+
Bid Categories |-|+
Apps/Accounts Blacklist  |+|+
Event Notification endpoint |+|-
Video Auction endpoint |+|+
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
