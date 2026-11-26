package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record OptoutBid(String requestId,
                        BigDecimal cpm,
                        String currency,
                        int width,
                        int height,
                        String ad,
                        Integer ttl,
                        String creativeId,
                        ObjectNode optOutExt,
                        String meta) {
}
