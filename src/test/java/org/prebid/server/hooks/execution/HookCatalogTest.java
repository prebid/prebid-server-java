package org.prebid.server.hooks.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class HookCatalogTest {

    @Mock
    private Module sampleModule;
    @Mock
    private Hook<?, ? extends InvocationContext> sampleHook;

    private HookCatalog hookCatalog;

    @BeforeEach
    public void setUp() {
        given(sampleModule.code()).willReturn("sample-module");

        hookCatalog = new HookCatalog(singleton(sampleModule));
    }

    @Test
    public void hookByIdShouldThrowExceptionOnUnknownModule() {
        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                hookCatalog.hookById(HookId.of("unknown-module", null), StageWithHookType.ENTRYPOINT));
    }

    @Test
    public void hookByIdShouldThrowExceptionOnUnknownHook() {
        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                hookCatalog.hookById(HookId.of("sample-module", "unknown-hook"), StageWithHookType.ENTRYPOINT));
    }

    @Test
    public void hookByIdShouldReturnEntrypointHook() {
        // given
        givenHook(EntrypointHook.class);

        // when
        final EntrypointHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.ENTRYPOINT);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnRawAuctionRequestHook() {
        // given
        givenHook(RawAuctionRequestHook.class);

        // when
        final RawAuctionRequestHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.RAW_AUCTION_REQUEST);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnProcessedAuctionRequestHook() {
        // given
        givenHook(ProcessedAuctionRequestHook.class);

        // when
        final ProcessedAuctionRequestHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.PROCESSED_AUCTION_REQUEST);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnBidderRequestHook() {
        // given
        givenHook(BidderRequestHook.class);

        // when
        final BidderRequestHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.BIDDER_REQUEST);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnRawBidderResponseHook() {
        // given
        givenHook(RawBidderResponseHook.class);

        // when
        final RawBidderResponseHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.RAW_BIDDER_RESPONSE);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnProcessedBidderResponseHook() {
        // given
        givenHook(ProcessedBidderResponseHook.class);

        // when
        final ProcessedBidderResponseHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.PROCESSED_BIDDER_RESPONSE);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    @Test
    public void hookByIdShouldReturnAuctionResponseHook() {
        // given
        givenHook(AuctionResponseHook.class);

        // when
        final AuctionResponseHook foundHook = hookCatalog.hookById(
                HookId.of("sample-module", "sample-hook"), StageWithHookType.AUCTION_RESPONSE);

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .isEqualTo("sample-hook");
    }

    private void givenHook(Class<? extends Hook<?, ? extends InvocationContext>> clazz) {
        sampleHook = mock(clazz);
        given(sampleHook.code()).willReturn("sample-hook");
        doReturn(singleton(sampleHook)).when(sampleModule).hooks();
    }
}
