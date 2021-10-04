package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.apache.commons.lang3.ObjectUtils;
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

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class DebugResolver {

    private static final String DEBUG_OVERRIDE_HEADER = "x-pbs-debug-override";
    private static final Boolean DEFAULT_DEBUG_ALLOWED_BY_ACCOUNT = true;

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
        final boolean debugEnabled = debugOverridden || isDebugEnabled(auctionContext, extRequestPrebid);

        final TraceLevel traceLevel = getIfNotNull(extRequestPrebid, ExtRequestPrebid::getTrace);

        return DebugContext.of(debugEnabled, debugOverridden, traceLevel);
    }

    private boolean isDebugOverridden(HttpRequestContext httpRequestContext) {
        return StringUtils.isNotEmpty(debugOverrideToken)
                && StringUtils.equals(httpRequestContext.getHeaders().get(DEBUG_OVERRIDE_HEADER), debugOverrideToken);
    }

    private boolean isDebugEnabled(AuctionContext auctionContext, ExtRequestPrebid extRequestPrebid) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final boolean debugEnabledForRequest = isDebugEnabledForRequest(bidRequest, extRequestPrebid);
        final boolean debugAllowedByAccount = isDebugAllowedByAccount(auctionContext);

        if (debugEnabledForRequest && !debugAllowedByAccount) {
            final List<String> warnings = auctionContext.getDebugWarnings();
            warnings.add("Debug turned off for account");
        }

        return debugEnabledForRequest && debugAllowedByAccount;
    }

    private boolean isDebugEnabledForRequest(BidRequest bidRequest, ExtRequestPrebid extRequestPrebid) {
        return Objects.equals(bidRequest.getTest(), 1)
                || Objects.equals(getIfNotNull(extRequestPrebid, ExtRequestPrebid::getDebug), 1);
    }

    private boolean isDebugAllowedByAccount(AuctionContext auctionContext) {
        final AccountAuctionConfig auctionConfig = getIfNotNull(auctionContext.getAccount(), Account::getAuction);
        final Boolean debugAllowedByAccount = getIfNotNull(auctionConfig, AccountAuctionConfig::getDebugAllow);
        return ObjectUtils.defaultIfNull(debugAllowedByAccount, DEFAULT_DEBUG_ALLOWED_BY_ACCOUNT);
    }

    public boolean resolveDebugForBidder(String bidderName, boolean debugEnabled, boolean debugOverride,
                                         List<String> warnings) {

        final boolean debugAllowedByBidder = bidderCatalog.isDebugAllowed(bidderName);

        if (debugEnabled && !debugAllowedByBidder) {
            warnings.add(String.format("Debug turned off for bidder: %s", bidderName));
        }

        return debugOverride || (debugEnabled && debugAllowedByBidder);
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
