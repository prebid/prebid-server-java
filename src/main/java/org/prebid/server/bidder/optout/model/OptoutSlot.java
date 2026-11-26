package org.prebid.server.bidder.optout.model;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record OptoutSlot(String adSlot, String id, List<AdSize> sizes, Map<String, String> customs) {
}
