package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.List;

/**
 * Per-request debug messages of the module. PBS shows a hook's debug messages in
 * {@code ext.prebid.modules.trace} only when the request asks for {@code ext.prebid.trace: verbose}, so the
 * messages are built only then.
 */
public final class HookTrace {

    private HookTrace() {
    }

    public static boolean isEnabled(AuctionInvocationContext invocationContext) {
        final AuctionContext auctionContext = invocationContext != null ? invocationContext.auctionContext() : null;
        return auctionContext != null
                && auctionContext.getDebugContext() != null
                && auctionContext.getDebugContext().getTraceLevel() == TraceLevel.verbose;
    }

    public static List<String> messages(boolean enabled, String message) {
        return enabled ? List.of(message) : null;
    }
}
