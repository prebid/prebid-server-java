package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GreenbidsAnalyticsReporterTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    GreenbidsAnalyticsReporter greenbidsAnalyticsReporter;

    AuctionEvent event;

    public static AuctionContext setupAuctionContext() {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final Format format = Format.builder()
                .w(320)
                .h(50)
                .build();

        final Imp imp = Imp.builder()
                .id("imp1")
                .banner(
                        Banner.builder()
                                .format(Collections.singletonList(format))
                                .build())
                .tagid("tag1")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .imp(Collections.singletonList(imp))
                .site(site)
                .build();

        // bid response
        final Bid bid = Bid.builder()
                .id("bid1")
                .impid("imp1")
                .price(BigDecimal.valueOf(1.5))
                .adm("<div>Ad Markup</div>")
                .build();

        final SeatBid seatBidWithBid = SeatBid.builder()
                .bid(Collections.singletonList(bid))
                .seat("seat1")
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .id("response1")
                .seatbid(Collections.singletonList(seatBidWithBid))
                .cur("USD")
                .build();

        final BidRejectionTracker bidRejectionTracker = new BidRejectionTracker(
                "seat2",
                Set.of("imp1"),
                1.0);

        bidRejectionTracker.reject("imp1", BidRejectionReason.NO_BID);

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .bidResponse(bidResponse)
                .bidRejectionTrackers(
                        Collections.singletonMap(
                                "seat2",
                                bidRejectionTracker))
                .build();
    }

    public static AuctionContext setUpAuctionContextWithNoBidResponse() {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .site(site)
                .build();

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .build();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final ObjectMapper mapper = ObjectMapperProvider.mapper();

        final JacksonMapper jacksonMapper = new JacksonMapper(mapper);

        final GreenbidsAnalyticsProperties greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
                .pbuid("pbuid1")
                .greenbidsSampling(1.0)
                .exploratorySamplingSplit(0.9)
                .analyticsServerVersion("2.2.0")
                .analyticsServer("http://localhost:8080")
                .configurationRefreshDelayMs(10000L)
                .timeoutMs(100000L)
                .build();

        greenbidsAnalyticsReporter = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient);
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContext() {
        // given
        final AuctionContext auctionContext = setupAuctionContext();
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = greenbidsAnalyticsReporter.processEvent(event);

        // then
        assertTrue(result.succeeded());
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
    }

    @Test
    public void shouldThrowExceptionWhenBidResponseIsNull() {
        // given
        final AuctionContext auctionContext = setUpAuctionContextWithNoBidResponse();
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = greenbidsAnalyticsReporter.processEvent(event);

        // then
        assertTrue(result.failed());
        assertEquals("Bid response or auction context cannot be null", result.cause().getMessage());
    }
}
