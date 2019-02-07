# Adding viewability support

This document describes how to handle viewability in Prebid Server

1. Choose vendor constants: These constants should be unique. The list of existing vendor constants can be found [here](../../src/main/java/org/prebid/server/bidder/ViewabilityVendors.java)

2. Add the constants to bidder-info: The list of vendors supported by your exchange is to be added to `../../src/main/resources/bidder-config/{bidder}.yaml` file 

3. Map constants to vendor urls in [this file](../../src/main/java/org/prebid/server/bidder/ViewabilityVendors.java). 

4. The adapter should be able to read the vendor constants from `bidrequest.imp[i].metric[j].vendor` and map it to the respective vendor url before making a request to the exchange.
