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

import java.util.ArrayList;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
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
    public void debugContextFromShouldSetDebugDisabledIfAbsentInBidRequestExt() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(builder -> builder.debug(null)))
                .account(givenAccountWithDebugConfig(true))
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, null));
    }

    @Test
    public void debugContextFromShouldSetDebugEnabledIfAccountAllowedAndDebugSetInBidRequestExt() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(builder -> builder.debug(1)))
                .account(givenAccountWithDebugConfig(true))
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, false, null));
    }

    @Test
    public void debugContextFromShouldDisableDebugIfAccountDebugIsNotAllowed() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);
        final ArrayList<String> warnings = new ArrayList<>();

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(builder -> builder.debug(1)))
                .account(givenAccountWithDebugConfig(false))
                .debugWarnings(warnings)
                .build();

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, null));
        assertThat(warnings).hasSize(1)
                .containsOnly("Debug turned off for account");
    }

    @Test
    public void debugContextFromShouldPassTraceLevelThrough() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(builder -> builder.trace(TraceLevel.basic)))
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
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, false, emptyList());

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void resolveDebugForBidderShouldReturnFalseIfDebugEnabledAndBidderDisallowedDebugAndDebugIsNotOverriden() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);
        final ArrayList<String> warnings = new ArrayList<>();

        given(bidderCatalog.isDebugAllowed("bidder")).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, false, warnings);

        // then
        assertThat(result).isFalse();
        assertThat(warnings).hasSize(1)
                .containsOnly("Debug turned off for bidder: bidder");
    }

    @Test
    public void resolveDebugForBidderShouldReturnTrueIfDebugOverriden() {
        // given
        final DebugResolver debugResolver = new DebugResolver(bidderCatalog, null);

        given(bidderCatalog.isDebugAllowed("bidder")).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder("bidder", true, true, new ArrayList<>());

        // then
        assertThat(result).isTrue();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<ExtRequestPrebid.ExtRequestPrebidBuilder> extRequestPrebidCustomizer) {

        return BidRequest.builder()
                .ext(ExtRequest.of(extRequestPrebidCustomizer.apply(ExtRequestPrebid.builder()).build()))
                .build();
    }

    private static Account givenAccountWithDebugConfig(boolean debugAllowed) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder().debugAllow(debugAllowed).build())
                .build();
    }
}
