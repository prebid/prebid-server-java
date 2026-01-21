package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.junit.jupiter.api.Test;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SamplingFetchFilterTest {

    @Test
    void shouldAcceptWhenRandomBelowRate() {
        // given
        final Random random = new Random() {
            @Override
            public double nextDouble() {
                return 0.1d;
            }
        };
        final SamplingFetchFilter filter = new SamplingFetchFilter(random, 0.25d);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(null);
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null);

        // when
        final FilterResult result = filter.shouldInvoke(payload, invocation);

        // then
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void shouldRejectWhenRandomAboveRate() {
        // given
        final Random random = new Random() {
            @Override
            public double nextDouble() {
                return 0.9d;
            }
        };
        final SamplingFetchFilter filter = new SamplingFetchFilter(random, 0.5d);

        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(null);
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null);

        // when
        final FilterResult result = filter.shouldInvoke(payload, invocation);

        // then
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("sampling");
    }
}
