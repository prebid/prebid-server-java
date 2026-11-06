package org.prebid.server.bidder.peak226.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class RegionPublisher {

    String region;

    String publisherId;
}
