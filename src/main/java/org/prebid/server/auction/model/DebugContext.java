package org.prebid.server.auction.model;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

@Value(staticConstructor = "of")
public class DebugContext {

    private static final DebugContext EMPTY = DebugContext.of(false, null);

    boolean debugEnabled;

    TraceLevel traceLevel;

    public static DebugContext empty() {
        return EMPTY;
    }
}
