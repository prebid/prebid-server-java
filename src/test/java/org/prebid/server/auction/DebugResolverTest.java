package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import org.junit.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountDebugConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class DebugResolverTest {

    @Test
    public void shouldSetDebugEnabledAndDebugOverrideIfDebugOverrideTokenHeaderPresentInHttpRequest() {
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
    public void shouldSetDebugEnabledIfPublisherAllowedAndDebugSetInBidRequestExt() {
        // given
        final DebugResolver debugResolver = new DebugResolver(null);

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
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(true, false, null));
    }

    @Test
    public void shouldDisableDebugIfPublisherDebugIsNotAllowed() {
        // given
        final DebugResolver debugResolver = new DebugResolver(null);

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
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, null));
    }

    @Test
    public void shouldPassTraceLevelThrough() {
        // given
        final DebugResolver debugResolver = new DebugResolver(null);

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
        final DebugContext result = debugResolver.getDebugContext(auctionContext);

        // then
        assertThat(result).isEqualTo(DebugContext.of(false, false, TraceLevel.basic));
    }

    private static Account givenAccountWithDebugConfig(boolean debugAllowed) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .debug(AccountDebugConfig.builder().allowed(debugAllowed).build())
                        .build())
                .build();
    }
}
