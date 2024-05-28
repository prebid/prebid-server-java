package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GreenbidsAnalyticsReporterTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private GreenbidsAnalyticsReporter target;

    private AuctionEvent event;

    private JacksonMapper jacksonMapper;

    private GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;

    @Before
    public void setUp() {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);

        greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
                .pbuid("pbuid1")
                .greenbidsSampling(1.0)
                .exploratorySamplingSplit(0.9)
                .analyticsServerVersion("2.2.0")
                .analyticsServer("http://localhost:8080")
                .configurationRefreshDelayMs(10000L)
                .timeoutMs(100000L)
                .build();

        target = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient);
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForBanner() {
        // given
        final Banner banner = setUpBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForVideo() {
        // given
        final Video video = setUpVideo();
        final Imp imp = Imp.builder()
                .video(video)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
    }

    @Test
    public void shouldFailWhenBidResponseIsNull() {
        // given
        final AuctionContext auctionContext = setUpAuctionContextWithNoBidResponse();
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Bid response or auction context cannot be null");
    }

    @Test
    public void shouldFailWhenAuctionContextIsNull() {
        // given
        final AuctionEvent event = mock(AuctionEvent.class);
        when(event.getAuctionContext()).thenReturn(null);

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Bid response or auction context cannot be null");
    }

    @Test
    public void shouldFailOnEncodeException() {
        // given
        final Banner banner = setUpBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final JacksonMapper mockJacksonMapper = mock(JacksonMapper.class);
        doThrow(new EncodeException("Failed to encode as JSON")).when(mockJacksonMapper).encodeToString(any());

        target = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                mockJacksonMapper,
                httpClient);

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Failed to encode as JSON");
    }

    @Test
    public void shouldFailOnUnexpectedResponseStatus() {
        // given
        final Banner banner = setUpBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(500);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Unexpected response status: 500");
    }

    @Test
    public void shouldFailWhenAdUnitsListIsEmpty() {
        // given
        final AuctionContext auctionContext = mock(AuctionContext.class);
        final BidResponse bidResponse = mock(BidResponse.class);
        when(auctionContext.getBidRequest()).thenReturn(mock(BidRequest.class));

        final AuctionEvent event = mock(AuctionEvent.class);
        when(event.getAuctionContext()).thenReturn(auctionContext);
        when(event.getBidResponse()).thenReturn(bidResponse);

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("AdUnits list should not be empty");
    }

    @Test
    public void shouldFailWhenBannerFormatIsNull() {
        // given
        final Banner bannerWithoutFormat = setUpBannerWithoutFormat();
        final Imp imp = Imp.builder()
                .banner(bannerWithoutFormat)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Error: Banner format should be non-null");
    }

    @Test
    public void shouldFailOnDecodingImpExtPrebid() {
        final String prebid = "prebid";
        final String bidderKey = "bidder";
        final String optionKey = "options";
        final String bidderName = "bidderName";
        final String optionValue = "1";

        final Map<String, Map<String, String>> bidderValue =
                singletonMap(
                        bidderName,
                        doubleMap("test-host", "unknownHost", "publisher_id", "ps4"));

        final ObjectMapper mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);

        final ObjectNode prebidJsonNodes = mapper.valueToTree(
                singletonMap(
                        prebid,
                        doubleMap(optionKey, optionValue, bidderKey, bidderValue)));

        final Imp imp = Imp.builder()
                .ext(prebidJsonNodes)
                .banner(setUpBanner())
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        target = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient);

        // when
        final Future<Void> result = target.processEvent(event);

        //then
        assertTrue(result.failed());
        assertThat(result.cause())
                .hasMessageStartingWith("Error decoding imp.ext.prebid: "
                        + "Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request.ExtOptions`");
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static AuctionContext setupAuctionContext(Imp imp) {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
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
                        singletonMap(
                                "seat2",
                                bidRejectionTracker))
                .build();
    }

    private static Banner setUpBanner() {
        final Format format = Format.builder()
                .w(320)
                .h(50)
                .build();

        return Banner.builder()
                .format(Collections.singletonList(format))
                .build();
    }

    private static Banner setUpBannerWithoutFormat() {
        return Banner.builder()
                .pos(1)
                .format(null)
                .build();
    }

    private static Video setUpVideo() {
        return Video.builder()
                .pos(1)
                .plcmt(1)
                .build();
    }

    private static AuctionContext setUpAuctionContextWithNoBidResponse() {
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
}
