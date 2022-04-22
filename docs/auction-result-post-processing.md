# Auction result post-processing

Companies hosting Prebid Server may have a need to make arbitrary post-processing of auction result, for example modify bids to include reporting callbacks etc.

This document describes mechanism of implementing such logic.

### 1. Implement your post-processor

Your post-processor needs to implement the `org.prebid.server.auction.BidResponsePostProcessor` interface.

Please make sure it behaves well i.e. _never_ throws exceptions (failed `Future` is OK though).

### 2. Add your implementation to Spring Context

In order to make Prebid Server aware of your implementation it needs to be added to the Spring Context in `org.prebid.server.spring.config.ServiceConfiguration` as a bean. It should be annotated with `@Primary` since there is a no-op implementation already defined in context.
