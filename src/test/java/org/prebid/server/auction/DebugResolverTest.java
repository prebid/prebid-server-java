package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class DebugResolverTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    @Test
    public void debugContextFromShouldSetDebugEnabledAndDebugOverrideIfDebugOverrideTokenHeaderPresentInHttpRequest() {
        // given
        final String debugOverrideToken = "override_token";
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, debugOverrideToken);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.builder()
                                .add("x-pbs-debug-override", debugOverrideToken)
                                .build())
                        .build())
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, true, null));
    }

    @Test
    public void debugContextFromShouldSetDebugEnabledIfPublisherAllowedAndDebugSetInBidRequestExt() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build()))
                        .build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.empty())
                        .build())
                .account(givenAccountWithDebugConfig(true))
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, false, null));
    }

    @Test
    public void debugContextFromShouldDisableDebugIfPublisherDebugIsNotAllowed() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build()))
                        .build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.empty())
                        .build())
                .account(givenAccountWithDebugConfig(false))
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, null));
    }

    @Test
    public void debugContextFromShouldPassTraceLevelThrough() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().trace(TraceLevel.basic).build()))
                        .build())
                .httpRequest(HttpRequestContext.builder()
                        .headers(CaseInsensitiveMultiMap.empty())
                        .build())
                .account(givenAccountWithDebugConfig(true))
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, TraceLevel.basic));
    }

    @Test
    public void resolveDebugForBidderShouldReturnTrueIfDebugEnabledAndBidderAllowedDebug() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        given(bidderCatalog.isDebugAllowed("bidder")).willReturn(true);

        // when
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, false);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void resolveDebugForBidderShouldReturnFalseIfDebugEnabledAndBidderDisallowedDebugAndDebugIsNotOverriden() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        given(bidderCatalog.isDebugAllowed("bidder")).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, false);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void resolveDebugForBidderShouldReturnTrueIfDebugOverriden() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        given(bidderCatalog.isDebugAllowed("bidder")).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, true);

        // then
        assertThat(result).isTrue();
    }

    private static Account givenAccountWithDebugConfig(boolean debugAllowed) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder().debugAllow(debugAllowed).build())
                .build();
    }
}
