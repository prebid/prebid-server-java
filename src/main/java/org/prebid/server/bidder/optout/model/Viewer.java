package org.prebid.server.bidder.optout.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Viewer {

    String id;

    String matcher;
}
