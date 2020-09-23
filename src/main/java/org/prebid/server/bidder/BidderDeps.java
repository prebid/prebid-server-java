package org.prebid.server.bidder;

import lombok.Value;

import java.util.List;

/**
 * Gathers all dependencies for different instances of the single bidder.
 */
@Value(staticConstructor = "of")
public class BidderDeps {

    List<BidderInstanceDeps> instances;
}
