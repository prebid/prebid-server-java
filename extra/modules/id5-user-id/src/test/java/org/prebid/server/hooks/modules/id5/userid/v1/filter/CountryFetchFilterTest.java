package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
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
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CountryFetchFilterTest {

    @Test
    void shouldUseGeoInfoCountryFirst() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed("PL")).thenReturn(true);
        final CountryFetchFilter filter = new CountryFetchFilter(vf);

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("US").build()).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc").build())
                .geoInfo(GeoInfo.builder().vendor("test").country("PL").build())
                .build();
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                auctionContext,
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, invocation);
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void shouldFallbackToDeviceGeoCountry() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed("US")).thenReturn(true);
        final CountryFetchFilter filter = new CountryFetchFilter(vf);

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("US").build()).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc").build())
                .build();
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                auctionContext,
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, invocation);
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void shouldRejectWhenCountryMissing() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        final CountryFetchFilter filter = new CountryFetchFilter(vf);

        final BidRequest bidRequest = BidRequest.builder().build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc").build())
                .build();
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                auctionContext,
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, invocation);
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("missing country");
    }

    @Test
    void shouldRejectWhenCountryNotAllowed() {
        final ValuesFilter<String> vf = Mockito.mock(ValuesFilter.class);
        when(vf.isValueAllowed("US")).thenReturn(false);
        final CountryFetchFilter filter = new CountryFetchFilter(vf);

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("US").build()).build())
                .build();
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(bidRequest);

        final Timeout timeout = new TimeoutFactory(Clock.systemUTC()).create(1000);
        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder().id("acc").build())
                .build();
        final AuctionInvocationContext invocation = AuctionInvocationContextImpl.of(
                InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                auctionContext,
                false,
                null,
                null);

        final FilterResult result = filter.shouldInvoke(payload, invocation);
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reason()).contains("country US rejected");
    }
}
