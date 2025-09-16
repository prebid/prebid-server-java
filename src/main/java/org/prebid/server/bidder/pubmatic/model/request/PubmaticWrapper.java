package org.prebid.server.bidder.pubmatic.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class PubmaticWrapper {

    Integer profile;

    Integer version;
}
