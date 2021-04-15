package org.prebid.server.hooks.execution.v1.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.model.Endpoint;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class AuctionInvocationContextImpl implements AuctionInvocationContext {

    Timeout timeout;

    Endpoint endpoint;

    boolean debugEnabled;

    ObjectNode accountConfig;
}
