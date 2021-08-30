package org.prebid.server.bidder.grid.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class KeywordSegment {

    String name;

    String value;
}

