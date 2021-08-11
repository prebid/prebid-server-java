package org.prebid.server.auction;


import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.Objects;
import java.util.function.Function;

public class DebugResolver {

    private static final String DEBUG_OVERRIDE_HEADER = "x-pbs-debug-override";

    private final String debugOverrideToken;

    private DebugResolver(String debugOverrideToken) {
        this.debugOverrideToken = debugOverrideToken;
    }

    public static DebugResolver withDebugOverrideCapability(String debugOverrideToken) {
        return new DebugResolver(debugOverrideToken);
    }

    public static DebugResolver withoutDebugOverrideCapability() {
        return new DebugResolver(null);
    }

    public DebugContext getDebugContext(HttpRequestContext httpRequestContext, BidRequest bidRequest) {
        final ExtRequestPrebid extRequestPrebid = getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);

        final boolean debugEnabled = Objects.equals(bidRequest.getTest(), 1)
                || Objects.equals(getIfNotNull(extRequestPrebid, ExtRequestPrebid::getDebug), 1)
                || isDebugOverriden(httpRequestContext);

        final TraceLevel traceLevel = getIfNotNull(extRequestPrebid, ExtRequestPrebid::getTrace);

        return DebugContext.of(debugEnabled, traceLevel);
    }

    private boolean isDebugOverriden(HttpRequestContext httpRequestContext) {
        return StringUtils.isNotEmpty(debugOverrideToken)
                && httpRequestContext.getHeaders().get(DEBUG_OVERRIDE_HEADER).equals(debugOverrideToken);
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
