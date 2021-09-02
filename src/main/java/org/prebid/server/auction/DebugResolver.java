package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;

import java.util.Objects;
import java.util.function.Function;

public class DebugResolver {

    private static final String DEBUG_OVERRIDE_HEADER = "x-pbs-debug-override";

    private final BidderCatalog bidderCatalog;
    private final String debugOverrideToken;

    public DebugResolver(BidderCatalog bidderCatalog, String debugOverrideToken) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.debugOverrideToken = debugOverrideToken;
    }

    public DebugContext debugContextFrom(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final ExtRequestPrebid extRequestPrebid = getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);

        final boolean debugOverridden = isDebugOverridden(auctionContext.getHttpRequest());
        final boolean debugEnabled = debugOverridden
                || (isAccountAllowed(auctionContext) && isDebugEnabled(bidRequest, extRequestPrebid));

        final TraceLevel traceLevel = getIfNotNull(extRequestPrebid, ExtRequestPrebid::getTrace);

        return DebugContext.of(debugEnabled, debugOverridden, traceLevel);
    }

    private boolean isAccountAllowed(AuctionContext auctionContext) {
        final AccountAuctionConfig auctionConfig = getIfNotNull(auctionContext.getAccount(), Account::getAuction);
        return BooleanUtils.toBoolean(getIfNotNull(auctionConfig, AccountAuctionConfig::getDebugAllow));
    }

    private boolean isDebugEnabled(BidRequest bidRequest, ExtRequestPrebid extRequestPrebid) {
        return Objects.equals(bidRequest.getTest(), 1)
                || Objects.equals(getIfNotNull(extRequestPrebid, ExtRequestPrebid::getDebug), 1);
    }

    private boolean isDebugOverridden(HttpRequestContext httpRequestContext) {
        return StringUtils.isNotEmpty(debugOverrideToken)
                && StringUtils.equals(httpRequestContext.getHeaders().get(DEBUG_OVERRIDE_HEADER), debugOverrideToken);
    }

    public boolean resolveDebugForBidder(String bidderName, boolean debugEnabled, boolean debugOverride) {
        return (bidderCatalog.isDebugAllowed(bidderName) && debugEnabled) || debugOverride;
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
