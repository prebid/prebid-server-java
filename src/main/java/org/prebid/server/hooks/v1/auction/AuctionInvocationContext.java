package org.prebid.server.hooks.v1.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.v1.InvocationContext;

public interface AuctionInvocationContext extends InvocationContext {

    boolean debugEnabled();

    ObjectNode accountConfig();
}
