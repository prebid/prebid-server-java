package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.junit.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;

import static org.assertj.core.api.Assertions.assertThat;

public class DebugResolverTest {

    @Test
    public void debugResolverShouldSetDebugEnabledAndDebugOverrideIfDebugOverrideTokenHeaderPresentInHttpRequest() {
        // given
        final String debugOverrideToken = "override_token";
        final DebugResolver debugResolver = new DebugResolver(debugOverrideToken);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder()
                                .add("x-pbs-debug-override", debugOverrideToken)
                                .build())
                        .build())
                .build();

        // when
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, true, null));
    }

    @Test
    public void debugResolverShouldSetDebugEnabledIfPublisherAllowedAndDebugSetInBidRequestExt() {
        // given
        final DebugResolver debugResolver = new DebugResolver(null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build()))
                        .build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.empty())
                        .build())
                .account(Account.builder().allowedDebug(true).build())
                .build();

        // when
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, false, null));
    }

    @Test
    public void debugResolverShouldDisableDebugIfPublisherDebugIsNotAllowed() {
        // given
        final DebugResolver debugResolver = new DebugResolver(null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build()))
                        .build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.empty())
                        .build())
                .account(Account.builder().allowedDebug(false).build())
                .build();

        // when
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, null));
    }
}
