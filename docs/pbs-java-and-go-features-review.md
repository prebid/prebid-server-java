# Feature Differences Overview

[Detailed Differences Description](differenceBetweenPBSGo-and-Java.md)

 Feature | Java | Go 
| --- | :---: | :---:|
Video Endpoint |-|+
First Party data |+|-
Universal Id |+|+
Currency Conversion** |+/-|+
Geolocation |+|-
Circuit Breaker |+|-
Passing Bidder ext in `imp[...].ext.prebid.bidder` |-|+
Media Type Price Granularity |+|-
Stored Responses |+|-
User IDs |+|-
Categories |-|+
`/event` endpoint |+|-
All adapters ported to OpenRTB |+|-
Bidder Generator |+|-


**
* PBS-Go Currency Conversion is disabled by default and not finalized yet (issue still open);
* PBS-Java Currency conversion supports finding intermediate conversion rate;
* PBS-Go Currency Conversion debug endpoint exposes more information, PBS-Java currently provides last updated time only;

