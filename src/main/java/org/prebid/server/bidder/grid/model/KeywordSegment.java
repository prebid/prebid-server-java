package org.prebid.server.bidder.grid.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class KeywordSegment {

    String name;

    String value;
}

