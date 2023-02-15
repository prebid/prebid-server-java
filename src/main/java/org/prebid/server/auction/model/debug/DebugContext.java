package org.prebid.server.auction.model.debug;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

@Value(staticConstructor = "of")
public class DebugContext {

    private static final DebugContext EMPTY = DebugContext.of(false, false, null);

    boolean debugEnabled;

    boolean shouldReturnAllBidStatuses;

    TraceLevel traceLevel;

    public static DebugContext empty() {
        return EMPTY;
    }
}
