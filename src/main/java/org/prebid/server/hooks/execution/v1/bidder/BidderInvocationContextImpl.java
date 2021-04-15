package org.prebid.server.hooks.execution.v1.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.model.Endpoint;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidderInvocationContextImpl implements BidderInvocationContext {

    Timeout timeout;

    Endpoint endpoint;

    boolean debugEnabled;

    ObjectNode accountConfig;

    String bidder;
}
