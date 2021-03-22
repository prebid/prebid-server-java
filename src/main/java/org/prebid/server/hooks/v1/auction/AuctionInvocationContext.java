package org.prebid.server.hooks.v1.auction;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.hooks.v1.InvocationContext;

public interface AuctionInvocationContext extends InvocationContext {

    boolean debugEnabled();

    JsonNode accountConfig();
}
