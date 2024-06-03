package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.math.BigDecimal;
import java.time.Clock;
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

public class GreenbidsAnalyticsReporterTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    @Mock
    private Clock clock;

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private GreenbidsAnalyticsReporter target;

    private AuctionEvent event;

    private GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;

    @Before
    public void setUp() {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);

        greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
                .exploratorySamplingSplit(0.9)
                .analyticsServerVersion("2.2.0")
                .analyticsServer("http://localhost:8080")
                .configurationRefreshDelayMs(10000L)
                .timeoutMs(100000L)
                .build();

        target = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient,
                clock,
                prebidVersionProvider);
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForBanner() throws JsonProcessingException {
        // given
        final Banner banner = setUpBanner();

        final ObjectNode prebidJsonNodes = mapper.valueToTree(
                singletonMap("gpid", TextNode.valueOf("gpidvalue")));

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(prebidJsonNodes)
                .banner(banner)
                .build();

        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);

        final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), jsonCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        final String capturedJson = jsonCaptor.getValue();
        final ObjectNode capturedJsonNode = (ObjectNode) mapper.readTree(capturedJson);
        capturedJsonNode.put("greenbidsId", "testGreenbidsId");
        capturedJsonNode.put("billingId", "testBillingId");
        final ObjectNode expectedJsonNode = (ObjectNode) mapper.readTree(expectedJsonForBannerTest());

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
        assertThat(capturedJsonNode).isEqualTo(expectedJsonNode);
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForVideo() throws JsonProcessingException {
        // given
        final Video video = setUpVideo();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .video(video)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);

        final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), jsonCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        final String capturedJson = jsonCaptor.getValue();
        final ObjectNode capturedJsonNode = (ObjectNode) mapper.readTree(capturedJson);
        capturedJsonNode.put("greenbidsId", "testGreenbidsId");
        capturedJsonNode.put("billingId", "testBillingId");
        final ObjectNode expectedJsonNode = (ObjectNode) mapper.readTree(expectedJsonForVideoTest());

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
        assertThat(capturedJsonNode).isEqualTo(expectedJsonNode);
    }

    @Test
    public void shouldReceiveValidResponseWhenBannerFormatIsNull() throws JsonProcessingException {
        // given
        final Banner bannerWithoutFormat = setUpBannerWithoutFormat();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .banner(bannerWithoutFormat)
                .build();
        final AuctionContext auctionContext = setupAuctionContext(imp);
        event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);

        final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), any(MultiMap.class), jsonCaptor.capture(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        final Future<Void> result = target.processEvent(event);

        final String capturedJson = jsonCaptor.getValue();
        final ObjectNode capturedJsonNode = (ObjectNode) mapper.readTree(capturedJson);
        capturedJsonNode.put("greenbidsId", "testGreenbidsId");
        capturedJsonNode.put("billingId", "testBillingId");
        final ObjectNode expectedJsonNode = (ObjectNode) mapper.readTree(expectedJsonForBannerWithFormatNullTest());

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(anyString(), any(MultiMap.class), anyString(), anyLong());
        verify(mockResponse).getStatusCode();
        assertThat(capturedJsonNode).isEqualTo(expectedJsonNode);
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
                httpClient,
                clock,
                prebidVersionProvider);

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
        when(auctionContext.getBidRequest())
                .thenReturn(BidRequest.builder()
                                .id("request1")
                                .ext(setUpExtRequest())
                                .build());

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
                httpClient,
                clock,
                prebidVersionProvider);

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
                .ext(setUpExtRequest())
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
                .w(240)
                .h(400)
                .build();
    }

    private static Banner setUpBannerWithoutFormat() {
        return Banner.builder()
                .pos(1)
                .format(null)
                .w(728)
                .h(90)
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
                .ext(setUpExtRequest())
                .build();

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .build();
    }

    private static ExtRequest setUpExtRequest() {
        final ObjectNode greenbidsNode = new ObjectMapper().createObjectNode();
        greenbidsNode.put("pbuid", "leparisien");
        greenbidsNode.put("greenbidsSampling", 1.0);

        final ObjectNode analyticsNode = new ObjectMapper().createObjectNode();
        analyticsNode.set("greenbids", greenbidsNode);

        return ExtRequest.of(
                ExtRequestPrebid
                        .builder()
                        .analytics(analyticsNode)
                        .build());
    }

    private String expectedJsonForBannerTest() {
        return "{"
                + "\"version\":\"2.2.0\","
                + "\"auctionId\":\"request1\","
                + "\"sampling\":1.0,"
                + "\"greenbidsId\":\"testGreenbidsId\","
                + "\"pbuid\":\"leparisien\","
                + "\"billingId\":\"testBillingId\","
                + "\"adUnits\":[{"
                + "\"code\":\"adunitcodevalue\","
                + "\"unifiedCode\":{"
                + "\"value\":\"gpidvalue\","
                + "\"src\":\"gpidSource\""
                + "},"
                + "\"mediaTypes\":{"
                + "\"banner\":{"
                + "\"sizes\":[["
                + "320,50"
                + "]]"
                + "}"
                + "},"
                + "\"bids\":[]"
                + "}],"
                + "\"auctionElapsed\":0"
                + "}";
    }

    private String expectedJsonForVideoTest() {
        return "{"
                + "\"version\":\"2.2.0\","
                + "\"auctionId\":\"request1\","
                + "\"sampling\":1.0,"
                + "\"greenbidsId\":\"testGreenbidsId\","
                + "\"pbuid\":\"leparisien\","
                + "\"billingId\":\"testBillingId\","
                + "\"adUnits\":[{"
                + "\"code\":\"adunitcodevalue\","
                + "\"unifiedCode\":{"
                + "\"value\":\"adunitcodevalue\","
                + "\"src\":\"adUnitCodeSource\""
                + "},"
                + "\"mediaTypes\":{"
                + "\"video\":{"
                + "\"plcmt\":1,"
                + "\"pos\":1"
                + "}"
                + "},"
                + "\"bids\":[]"
                + "}],"
                + "\"auctionElapsed\":0"
                + "}";
    }

    private String expectedJsonForBannerWithFormatNullTest() {
        return "{"
                + "\"version\":\"2.2.0\","
                + "\"auctionId\":\"request1\","
                + "\"sampling\":1.0,"
                + "\"greenbidsId\":\"testGreenbidsId\","
                + "\"pbuid\":\"leparisien\","
                + "\"billingId\":\"testBillingId\","
                + "\"adUnits\":[{"
                + "\"code\":\"adunitcodevalue\","
                + "\"unifiedCode\":{"
                + "\"value\":\"adunitcodevalue\","
                + "\"src\":\"adUnitCodeSource\""
                + "},"
                + "\"mediaTypes\":{"
                + "\"banner\":{"
                + "\"sizes\":[["
                + "728,90"
                + "]],"
                + "\"pos\":1"
                + "}"
                + "},"
                + "\"bids\":[]"
                + "}],"
                + "\"auctionElapsed\":0"
                + "}";
    }
}
