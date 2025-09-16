package org.prebid.server.bidder.beachfront.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class BeachfrontSize {

    Integer w;

    Integer h;
}
