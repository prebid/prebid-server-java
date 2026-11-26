package org.prebid.server.bidder.optout.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Viewer(String id, String matcher) {

}
