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
import org.prebid.server.util.ObjectUtil;

import java.util.Objects;

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
        final boolean debugEnabled = isDebugEnabled(auctionContext);
        final TraceLevel traceLevel = getTraceLevel(auctionContext.getBidRequest());
        return DebugContext.of(debugEnabled, traceLevel);
    }

    private boolean isDebugEnabled(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final boolean debugOverride = isDebugOverridden(auctionContext.getHttpRequest());
        final boolean debugEnabledForRequest = isDebugEnabledForRequest(bidRequest);
        final boolean debugAllowedByAccount = isDebugAllowedByAccount(auctionContext.getAccount());

        if (debugEnabledForRequest && !debugOverride && !debugAllowedByAccount) {
            auctionContext.getDebugWarnings()
                    .add("Debug turned off for account");
        }

        return debugOverride || (debugEnabledForRequest && debugAllowedByAccount);
    }

    private boolean isDebugOverridden(HttpRequestContext httpRequest) {
        return StringUtils.isNotEmpty(debugOverrideToken)
                && StringUtils.equals(httpRequest.getHeaders().get(DEBUG_OVERRIDE_HEADER), debugOverrideToken);
    }

    private boolean isDebugEnabledForRequest(BidRequest bidRequest) {
        return Objects.equals(bidRequest.getTest(), 1)
                || Objects.equals(ObjectUtil.getIfNotNull(getExtRequestPrebid(bidRequest),
                ExtRequestPrebid::getDebug), 1);
    }

    private boolean isDebugAllowedByAccount(Account account) {
        final AccountAuctionConfig auctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);
        final Boolean debugAllowed = ObjectUtil.getIfNotNull(auctionConfig, AccountAuctionConfig::getDebugAllow);
        return ObjectUtils.defaultIfNull(debugAllowed, DEFAULT_DEBUG_ALLOWED_BY_ACCOUNT);
    }

    private static TraceLevel getTraceLevel(BidRequest bidRequest) {
        return ObjectUtil.getIfNotNull(getExtRequestPrebid(bidRequest), ExtRequestPrebid::getTrace);
    }

    private static ExtRequestPrebid getExtRequestPrebid(BidRequest bidRequest) {
        return ObjectUtil.getIfNotNull(
                ObjectUtil.getIfNotNull(bidRequest, BidRequest::getExt), ExtRequest::getPrebid);
    }

    public boolean resolveDebugForBidder(AuctionContext auctionContext, String bidder) {
        final DebugContext debugContext = auctionContext.getDebugContext();
        final boolean debugEnabled = debugContext.isDebugEnabled();
        final boolean debugOverride = isDebugOverridden(auctionContext.getHttpRequest());
        final boolean debugAllowedByBidder = bidderCatalog.isDebugAllowed(bidder);

        if (debugEnabled && !debugOverride && !debugAllowedByBidder) {
            auctionContext.getDebugWarnings()
                    .add(String.format("Debug turned off for bidder: %s", bidder));
        }

        return debugOverride || (debugEnabled && debugAllowedByBidder);
    }
}
