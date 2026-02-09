package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.config.ValuesFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;
import org.prebid.server.execution.timeout.TimeoutFactory;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AccountFetchFilterTest {

    @Test
    void shouldAcceptWhenAccountAllowed() {
        final ValuesFilter<String> valuesFilter = Mockito.mock(ValuesFilter.class);
        when(valuesFilter.isValueAllowed(eq("acc-2"))).thenReturn(true);
        final AccountFetchFilter filter = new AccountFetchFilter(valuesFilter);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(null);
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext ctx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc-2").build()).build(),
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, ctx);
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void shouldRejectWhenAccountNotAllowed() {
        final ValuesFilter<String> valuesFilter = Mockito.mock(ValuesFilter.class);
        when(valuesFilter.isValueAllowed(eq("acc-3"))).thenReturn(false);
        final AccountFetchFilter filter = new AccountFetchFilter(valuesFilter);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(null);
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext ctx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc-3").build()).build(),
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, ctx);
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("account acc-3 rejected");
    }

    @Test
    void shouldRejectWhenAccountMissing() {
        final ValuesFilter<String> valuesFilter = Mockito.mock(ValuesFilter.class);
        final AccountFetchFilter filter = new AccountFetchFilter(valuesFilter);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(null);
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext ctx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().build(),
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, ctx);
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("missing account id");
    }
}
