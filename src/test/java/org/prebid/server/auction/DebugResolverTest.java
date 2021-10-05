package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.junit.Before;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class DebugResolverTest {

    private static final String DEBUG_OVERRIDE_TOKEN = "debug_override_token";
    private static final CaseInsensitiveMultiMap HEADERS_WITH_DEBUG_OVERRIDE_TOKEN = CaseInsensitiveMultiMap.builder()
            .add("x-pbs-debug-override", DEBUG_OVERRIDE_TOKEN)
            .build();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private DebugResolver debugResolver;

    @Before
    public void setUp() {
        debugResolver = new DebugResolver(bidderCatalog, null);
    }

    @Test
    public void debugContextFromShouldSetDebugEnabledAndDebugOverrideIfDebugOverrideTokenHeaderPresentInHttpRequest() {
        // given
        debugResolver = new DebugResolver(bidderCatalog, DEBUG_OVERRIDE_TOKEN);

        final AuctionContext auctionContext = givenAuctionContext(builder -> builder
                .bidRequest(givenBidRequest(extPrebid -> extPrebid.debug(0))) // will be ignored
                .httpRequest(HttpRequestContext.builder().headers(HEADERS_WITH_DEBUG_OVERRIDE_TOKEN).build()));

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, null));
    }

    @Test
    public void debugContextFromShouldSetDebugDisabledIfAccountDebugIsAllowedButAbsentInBidRequestExt() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder
                .bidRequest(givenBidRequest(extPrebid -> extPrebid.debug(null)))
                .account(givenAccount(true)));

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, null));
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void debugContextFromShouldSetDebugDisabledIfAccountDebugIsNotAllowed() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder
                .bidRequest(givenBidRequest(extPrebid -> extPrebid.debug(1)))
                .account(givenAccount(false)));

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, null));
        assertThat(auctionContext.getDebugWarnings()).hasSize(1)
                .containsOnly("Debug turned off for account");
    }

    @Test
    public void debugContextFromShouldSetDebugEnabledIfAccountAllowedAndDebugSetInBidRequestExt() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder
                .bidRequest(givenBidRequest(extPrebid -> extPrebid.debug(1)))
                .account(givenAccount(true)));

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, null));
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void debugContextFromShouldPassTraceLevelThrough() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(builder -> builder
                .bidRequest(givenBidRequest(extPrebid -> extPrebid.trace(TraceLevel.basic)))
                .account(givenAccount(true)));

        // when
        final DebugContext result = debugResolver.debugContextFrom(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, TraceLevel.basic));
    }

    @Test
    public void resolveDebugForBidderShouldReturnFalseIfDebugDisabledAndNotOverridden() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(false, false);

        given(bidderCatalog.isDebugAllowed(anyString())).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder(auctionContext, "bidder");

        // then
        assertThat(result).isFalse();
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void resolveDebugForBidderShouldReturnFalseIfDebugEnabledButBidderDisallowedDebugAndDebugIsNotOverridden() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(true, false);

        given(bidderCatalog.isDebugAllowed(anyString())).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder(auctionContext, "bidder");

        // then
        assertThat(result).isFalse();
        assertThat(auctionContext.getDebugWarnings()).hasSize(1)
                .containsOnly("Debug turned off for bidder: bidder");
    }

    @Test
    public void resolveDebugForBidderShouldReturnTrueIfDebugEnabledAndBidderAllowedDebug() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(true, false);

        given(bidderCatalog.isDebugAllowed(anyString())).willReturn(true);

        // when
        final boolean result = debugResolver.resolveDebugForBidder(auctionContext, "bidder");

        // then
        assertThat(result).isTrue();
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void resolveDebugForBidderShouldReturnTrueIfDebugOverridden() {
        // given
        debugResolver = new DebugResolver(bidderCatalog, DEBUG_OVERRIDE_TOKEN);

        final AuctionContext auctionContext = givenAuctionContext(false, true);

        given(bidderCatalog.isDebugAllowed(anyString())).willReturn(false);

        // when
        final boolean result = debugResolver.resolveDebugForBidder(auctionContext, "bidder");

        // then
        assertThat(result).isTrue();
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    private static AuctionContext givenAuctionContext(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        return auctionContextCustomizer.apply(AuctionContext.builder()
                        .debugWarnings(new ArrayList<>()))
                .build();
    }

    private static AuctionContext givenAuctionContext(boolean debugEnabled, boolean debugOverride) {
        final HttpRequestContext httpRequestContext = debugOverride
                ? HttpRequestContext.builder().headers(HEADERS_WITH_DEBUG_OVERRIDE_TOKEN).build()
                : null;

        return givenAuctionContext(builder -> builder
                .httpRequest(httpRequestContext)
                .debugContext(DebugContext.of(debugEnabled, null)));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<ExtRequestPrebid.ExtRequestPrebidBuilder> extRequestPrebidCustomizer) {

        return BidRequest.builder()
                .ext(ExtRequest.of(extRequestPrebidCustomizer.apply(ExtRequestPrebid.builder()).build()))
                .build();
    }

    private static Account givenAccount(boolean debugAllowed) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder().debugAllow(debugAllowed).build())
                .build();
    }
}
