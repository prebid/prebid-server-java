package org.prebid.server.hooks.modules.ortb2.blocking.v1.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.model.Endpoint;

@Accessors(fluent = true)
@Builder
@Value
public class BidderInvocationContextImpl implements BidderInvocationContext {

    Timeout timeout;

    Endpoint endpoint;

    Object moduleContext;

    boolean debugEnabled;

    ObjectNode accountConfig;

    String bidder;

    public static BidderInvocationContext of(String bidder, ObjectNode accountConfig, boolean debugEnabled) {
        return BidderInvocationContextImpl.builder()
            .bidder(bidder)
            .accountConfig(accountConfig)
            .debugEnabled(debugEnabled)
            .build();
    }
}
