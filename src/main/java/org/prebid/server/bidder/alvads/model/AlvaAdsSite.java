package org.prebid.server.bidder.alvads.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class AlvaAdsSite {

    String page;

    String ref;

    Map<String, Object> publisher;
}
