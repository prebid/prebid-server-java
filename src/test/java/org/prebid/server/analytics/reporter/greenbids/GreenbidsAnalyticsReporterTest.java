package org.prebid.server.analytics.reporter.greenbids;

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
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBids;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsUnifiedCode;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
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

    @Captor
    private ArgumentCaptor<MultiMap> headersCaptor;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    @Captor
    private ArgumentCaptor<Long> timeoutCaptor;

    private GreenbidsAnalyticsReporter target;

    private GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;

    @Before
    public void setUp() {
        greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
                .exploratorySamplingSplit(0.9)
                .analyticsServerVersion("2.2.0")
                .analyticsServerUrl("http://localhost:8080")
                .configurationRefreshDelayMs(10000L)
                .timeoutMs(100000L)
                .build();

        target = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient,
                clock,
                prebidVersionProvider);

        jsonCaptor = ArgumentCaptor.forClass(String.class);
        headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        urlCaptor = ArgumentCaptor.forClass(String.class);
        timeoutCaptor = ArgumentCaptor.forClass(Long.class);
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForBanner() throws IOException {
        // given
        final Banner banner = givenBanner();

        final ObjectNode prebidJsonNodes = mapper.valueToTree(
                singletonMap("gpid", TextNode.valueOf("gpidvalue")));

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(prebidJsonNodes)
                .banner(banner)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = givenCommonMessageForBanner();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(
                urlCaptor.capture(), headersCaptor.capture(), jsonCaptor.capture(), timeoutCaptor.capture());
        verify(mockResponse).getStatusCode();

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = mapper.readValue(capturedJson, CommonMessage.class);
        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080");
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(timeoutCaptor.getValue()).isEqualTo(greenbidsAnalyticsProperties.getTimeoutMs());
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForVideo() throws IOException {
        // given
        final Video video = givenVideo();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .video(video)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = givenCommonMessageForVideo();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(
                urlCaptor.capture(), headersCaptor.capture(), jsonCaptor.capture(), timeoutCaptor.capture());
        verify(mockResponse).getStatusCode();

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = mapper.readValue(capturedJson, CommonMessage.class);
        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080");
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(timeoutCaptor.getValue()).isEqualTo(greenbidsAnalyticsProperties.getTimeoutMs());
    }

    @Test
    public void shouldReceiveValidResponseWhenBannerFormatIsNull() throws IOException {
        // given
        final Banner bannerWithoutFormat = givenBannerWithoutFormat();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .banner(bannerWithoutFormat)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = givenCommonMessageBannerWithoutFormat();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(
                urlCaptor.capture(), headersCaptor.capture(), jsonCaptor.capture(), timeoutCaptor.capture());
        verify(mockResponse).getStatusCode();

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = mapper.readValue(capturedJson, CommonMessage.class);
        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080");
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(timeoutCaptor.getValue()).isEqualTo(greenbidsAnalyticsProperties.getTimeoutMs());
    }

    @Test
    public void shouldFailWhenBidResponseIsNull() {
        // given
        final AuctionContext auctionContext = givenAuctionContextWithNoBidResponse();
        final AuctionEvent event = AuctionEvent.builder()
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
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
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
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
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
                                .ext(givenExtRequest())
                                .build());

        final AuctionEvent event = mock(AuctionEvent.class);
        when(event.getAuctionContext()).thenReturn(auctionContext);
        when(event.getBidResponse()).thenReturn(bidResponse);

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Impressions list should not be empty");
    }

    @Test
    public void shouldFailOnDecodingImpExtPrebid() {
        // given
        final String prebid = "prebid";
        final String bidderKey = "bidder";
        final String optionKey = "options";
        final String bidderName = "bidderName";
        final String optionValue = "1";

        final Map<String, Map<String, String>> bidderValue =
                singletonMap(
                        bidderName,
                        Map.of("test-host", "unknownHost", "publisher_id", "ps4"));

        final ObjectNode prebidJsonNodes = mapper.valueToTree(
                singletonMap(
                        prebid,
                        Map.of(optionKey, optionValue, bidderKey, bidderValue)));

        final Imp imp = Imp.builder()
                .ext(prebidJsonNodes)
                .banner(givenBanner())
                .build();
        final AuctionContext auctionContext = givenAuctionContext(imp);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = target.processEvent(event);

        //then
        assertTrue(result.failed());
        assertThat(result.cause())
                .hasMessageStartingWith("Error decoding imp.ext.prebid: "
                        + "Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request.ExtOptions`");
    }

    private static AuctionContext givenAuctionContext(Imp imp) {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .imp(Collections.singletonList(imp))
                .site(site)
                .ext(givenExtRequest())
                .build();

        // bid response
        final Bid bid = Bid.builder()
                .id("bid1")
                .impid("adunitcodevalue")
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
                Set.of("adunitcodevalue"),
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

    private static Banner givenBanner() {
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

    private static Banner givenBannerWithoutFormat() {
        return Banner.builder()
                .pos(1)
                .format(null)
                .w(728)
                .h(90)
                .build();
    }

    private static Video givenVideo() {
        return Video.builder()
                .pos(1)
                .plcmt(1)
                .build();
    }

    private static AuctionContext givenAuctionContextWithNoBidResponse() {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .site(site)
                .ext(givenExtRequest())
                .build();

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .build();
    }

    private static ExtRequest givenExtRequest() {
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

    private static CommonMessage givenCommonMessageForBanner() {
        return CommonMessage.builder()
                .version("2.2.0")
                .auctionId("request1")
                .sampling(1.0)
                .pbuid("leparisien")
                .adUnits(
                        List.of(GreenbidsAdUnit.builder()
                                .code("adunitcodevalue")
                                .unifiedCode(GreenbidsUnifiedCode.builder()
                                        .value("gpidvalue")
                                        .source("gpidSource")
                                        .build())
                                .mediaTypes(MediaTypes.builder()
                                        .banner(ExtBanner.builder()
                                                .sizes(List.of(List.of(320, 50)))
                                                .build())
                                        .build())
                                .bids(List.of(
                                        GreenbidsBids.builder()
                                                .bidder("seat1")
                                                .isTimeout(false)
                                                .hasBid(true)
                                                .build(),
                                        GreenbidsBids.builder()
                                                .bidder("seat2")
                                                .isTimeout(false)
                                                .hasBid(false)
                                                .build()
                                ))
                                .build()
                        ))
                .auctionElapsed(0L)
                .build();
    }

    private static CommonMessage givenCommonMessageForVideo() {
        return CommonMessage.builder()
                .version("2.2.0")
                .auctionId("request1")
                .sampling(1.0)
                .pbuid("leparisien")
                .adUnits(
                        List.of(GreenbidsAdUnit.builder()
                                .code("adunitcodevalue")
                                .unifiedCode(GreenbidsUnifiedCode.builder()
                                        .value("adunitcodevalue")
                                        .source("adUnitCodeSource")
                                        .build())
                                .mediaTypes(MediaTypes.builder()
                                        .video(Video.builder()
                                                .plcmt(1)
                                                .pos(1)
                                                .build())
                                        .build())
                                .bids(List.of(
                                        GreenbidsBids.builder()
                                                .bidder("seat1")
                                                .isTimeout(false)
                                                .hasBid(true)
                                                .build(),
                                        GreenbidsBids.builder()
                                                .bidder("seat2")
                                                .isTimeout(false)
                                                .hasBid(false)
                                                .build()
                                ))
                                .build()
                        ))
                .auctionElapsed(0L)
                .build();
    }

    private static CommonMessage givenCommonMessageBannerWithoutFormat() {
        return CommonMessage.builder()
                .version("2.2.0")
                .auctionId("request1")
                .sampling(1.0)
                .pbuid("leparisien")
                .adUnits(
                        List.of(GreenbidsAdUnit.builder()
                                .code("adunitcodevalue")
                                .unifiedCode(GreenbidsUnifiedCode.builder()
                                        .value("adunitcodevalue")
                                        .source("adUnitCodeSource")
                                        .build())
                                .mediaTypes(MediaTypes.builder()
                                        .banner(ExtBanner.builder()
                                                .sizes(List.of(List.of(728, 90)))
                                                .pos(1)
                                                .build())
                                        .build())
                                .bids(List.of(
                                        GreenbidsBids.builder()
                                                .bidder("seat1")
                                                .isTimeout(false)
                                                .hasBid(true)
                                                .build(),
                                        GreenbidsBids.builder()
                                                .bidder("seat2")
                                                .isTimeout(false)
                                                .hasBid(false)
                                                .build()
                                ))
                                .build()
                        ))
                .auctionElapsed(0L)
                .build();
    }
}
