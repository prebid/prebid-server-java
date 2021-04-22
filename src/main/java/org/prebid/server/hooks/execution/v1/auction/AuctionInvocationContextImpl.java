package org.prebid.server.hooks.execution.v1.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.model.Endpoint;

@Accessors(fluent = true)
@Builder
@Value
public class AuctionInvocationContextImpl implements AuctionInvocationContext {

    Timeout timeout;

    Endpoint endpoint;

    boolean debugEnabled;

    ObjectNode accountConfig;

    Object moduleContext;
}
