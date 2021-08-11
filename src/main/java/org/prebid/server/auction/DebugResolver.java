package org.prebid.server.auction;


import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;

import java.util.Objects;
import java.util.function.Function;

public class DebugResolver {

    private static final String DEBUG_OVERRIDE_HEADER = "x-pbs-debug-override";

    private final String debugOverrideToken;

    public DebugResolver(String debugOverrideToken) {
        this.debugOverrideToken = debugOverrideToken;
    }

    public DebugContext getDebugContext(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final ExtRequestPrebid extRequestPrebid = getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);

        final boolean publisherAllowed = isPublisherAllowed(auctionContext);
        final boolean debugOverridden = isDebugOverridden(auctionContext.getHttpRequest());
        final boolean debugEnabled = (publisherAllowed && isDebugEnabled(bidRequest, extRequestPrebid))
                || debugOverridden;

        final TraceLevel traceLevel = getIfNotNull(extRequestPrebid, ExtRequestPrebid::getTrace);

        return DebugContext.of(debugEnabled, debugOverridden, traceLevel);
    }

    private boolean isPublisherAllowed(AuctionContext auctionContext) {
        return BooleanUtils.toBoolean(getIfNotNull(auctionContext.getAccount(), Account::getAllowedDebug));
    }

    private boolean isDebugEnabled(BidRequest bidRequest, ExtRequestPrebid extRequestPrebid) {
        return Objects.equals(bidRequest.getTest(), 1)
                || Objects.equals(getIfNotNull(extRequestPrebid, ExtRequestPrebid::getDebug), 1);
    }

    private boolean isDebugOverridden(HttpRequestContext httpRequestContext) {
        return StringUtils.isNotEmpty(debugOverrideToken)
                && httpRequestContext.getHeaders().get(DEBUG_OVERRIDE_HEADER).equals(debugOverrideToken);
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
