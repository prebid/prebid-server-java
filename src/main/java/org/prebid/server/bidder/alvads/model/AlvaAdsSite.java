package org.prebid.server.bidder.alvads.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AlvaAdsSite {

    private String page;
    private String ref;
    private Map<String, Object> publisher;
}
