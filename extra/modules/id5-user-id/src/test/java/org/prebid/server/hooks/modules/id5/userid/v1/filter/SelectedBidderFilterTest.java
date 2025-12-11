package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.config.ValuesFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.model.Endpoint;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.execution.timeout.TimeoutFactory;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SelectedBidderFilterTest {

    private BidderInvocationContextImpl bidderCtx(String bidder) {
        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionInvocationContext auctionCtx = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                AuctionContext.builder().account(Account.builder().id("acc").build()).build(),
                false,
                null,
                null);
        return BidderInvocationContextImpl.of(auctionCtx, bidder);
    }

    @Test
    void shouldAcceptWhenBidderAllowed() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed(any())).thenReturn(true);
        final SelectedBidderFilter filter = new SelectedBidderFilter(vf);

        final FilterResult result = filter.shouldInvoke(BidderRequestPayloadImpl.of(null), bidderCtx("rubicon"));
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void shouldRejectWhenBidderNotAllowed() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed(any())).thenReturn(false);
        final SelectedBidderFilter filter = new SelectedBidderFilter(vf);

        final FilterResult result = filter.shouldInvoke(BidderRequestPayloadImpl.of(null), bidderCtx("pubmatic"));
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("bidder pubmatic rejected");
    }

    @Test
    void shouldDelegateDecisionToValuesFilter() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed("anything")).thenReturn(true);
        final SelectedBidderFilter filter = new SelectedBidderFilter(vf);

        final FilterResult result = filter.shouldInvoke(BidderRequestPayloadImpl.of(null), bidderCtx("anything"));
        assertThat(result.isAccepted()).isTrue();
    }
}
