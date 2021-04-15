package org.prebid.server.hooks.execution;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

public class HookCatalogTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Module sampleModule;
    @Mock
    private Hook sampleHook;

    private HookCatalog hookCatalog;

    @Before
    public void setUp() {
        given(sampleModule.code()).willReturn("sample-module");

        hookCatalog = new HookCatalog(singleton(sampleModule));
    }

    @Test
    public void entrypointHookByShouldTolerateUnknownModule() {
        // when
        final Hook foundHook = hookCatalog.entrypointHookBy("unknown-module", null);

        // then
        assertThat(foundHook).isNull();
    }

    @Test
    public void entrypointHookByShouldTolerateUnknownHook() {
        // when
        final Hook foundHook = hookCatalog.entrypointHookBy("sample-module", "unknown-hook");

        // then
        assertThat(foundHook).isNull();
    }

    @Test
    public void entrypointHookByShouldReturnExpectedResult() {
        // given
        givenHook(EntrypointHook.class);

        // when
        final Hook foundHook = hookCatalog.entrypointHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .containsOnly("sample-hook");
    }

    @Test
    public void rawAuctionRequestHookByShouldReturnExpectedResult() {
        // given
        givenHook(RawAuctionRequestHook.class);

        // when
        final Hook foundHook = hookCatalog.rawAuctionRequestHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .containsOnly("sample-hook");
    }

    @Test
    public void bidderRequestHookByShouldReturnExpectedResult() {
        // given
        givenHook(BidderRequestHook.class);

        // when
        final Hook foundHook = hookCatalog.bidderRequestHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .containsOnly("sample-hook");
    }

    @Test
    public void rawBidderResponseHookByShouldReturnExpectedResult() {
        // given
        givenHook(RawBidderResponseHook.class);

        // when
        final Hook foundHook = hookCatalog.rawBidderResponseHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .containsOnly("sample-hook");
    }

    @Test
    public void auctionResponseHookByShouldReturnExpectedResult() {
        // given
        givenHook(AuctionResponseHook.class);

        // when
        final Hook foundHook = hookCatalog.auctionResponseHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::code)
                .containsOnly("sample-hook");
    }

    private void givenHook(Class<? extends Hook> clazz) {
        sampleHook = Mockito.mock(clazz);
        given(sampleHook.code()).willReturn("sample-hook");
        doReturn(singleton(sampleHook)).when(sampleModule).hooks();
    }
}
