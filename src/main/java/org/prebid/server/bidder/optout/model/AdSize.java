package org.prebid.server.bidder.optout.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record AdSize(Integer w, Integer h) {
}
