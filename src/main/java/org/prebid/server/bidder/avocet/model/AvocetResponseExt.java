package org.prebid.server.bidder.avocet.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class AvocetResponseExt {

    AvocetBidExtension avocet;
}
