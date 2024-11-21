package org.prebid.server.hooks.v1.exit;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

public interface ExitpointHook extends Hook<ExitpointPayload, AuctionInvocationContext> {
}
