package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBid;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsUnifiedCode;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpResult;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsActivity;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GreenbidsAnalyticsReporterTest extends VertxTest {

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    @Captor
    private ArgumentCaptor<MultiMap> headersCaptor;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Clock clock;

    @Mock
    private PrebidVersionProvider prebidVersionProvider;

    private GreenbidsAnalyticsReporter target;

    private GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;

    private JacksonMapper jacksonMapper;

    @BeforeEach
    public void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);

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
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextWithAnalyticsTagForBanner() throws IOException {
        // given
        final Banner banner = givenBanner();

        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("gpid", TextNode.valueOf("gpidvalue"));
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(impExtNode)
                .banner(banner)
                .build();

        final AuctionContext auctionContext = givenAuctionContextWithAnalyticsTag(
                context -> context, List.of(imp), true, true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = givenCommonMessageForBannerWithRtb2Imp();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.succeeded()).isTrue();
        verify(httpClient).post(
                eq(greenbidsAnalyticsProperties.getAnalyticsServerUrl()),
                headersCaptor.capture(),
                jsonCaptor.capture(),
                eq(greenbidsAnalyticsProperties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = jacksonMapper.mapper()
                .readValue(capturedJson, CommonMessage.class);

        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(capturedCommonMessage.getGreenbidsId()).isNotNull();
        assertThat(capturedCommonMessage.getBillingId()).isNotNull();
        capturedCommonMessage.getAdUnits().forEach(adUnit -> {
            assertThat(adUnit.getOrtb2ImpResult().getExt().getGreenbids().getFingerprint()).isNotNull();
            assertThat(adUnit.getOrtb2ImpResult().getExt().getTid()).isNotNull();
        });

        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForBanner() throws IOException {
        // given
        final Banner banner = givenBanner();

        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("gpid", TextNode.valueOf("gpidvalue"));
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(impExtNode)
                .banner(banner)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = expectedCommonMessageForBanner();

        // when
        target.processEvent(event);

        // then
        verify(httpClient).post(
                eq(greenbidsAnalyticsProperties.getAnalyticsServerUrl()),
                any(MultiMap.class),
                jsonCaptor.capture(),
                eq(greenbidsAnalyticsProperties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = jacksonMapper.mapper()
                .readValue(capturedJson, CommonMessage.class);

        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(capturedCommonMessage.getGreenbidsId()).isNotNull();
        assertThat(capturedCommonMessage.getBillingId()).isNotNull();
    }

    @Test
    public void shouldReceiveValidResponseOnAuctionContextForVideo() throws IOException {
        // given
        final Video video = givenVideo();

        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(impExtNode)
                .video(video)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(anyString(), any(MultiMap.class), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = expectedCommonMessageForVideo();

        // when
        target.processEvent(event);

        // then
        verify(httpClient).post(
                eq(greenbidsAnalyticsProperties.getAnalyticsServerUrl()),
                any(MultiMap.class),
                jsonCaptor.capture(),
                eq(greenbidsAnalyticsProperties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = jacksonMapper.mapper()
                .readValue(capturedJson, CommonMessage.class);
        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(capturedCommonMessage.getGreenbidsId()).isNotNull();
        assertThat(capturedCommonMessage.getBillingId()).isNotNull();
    }

    @Test
    public void shouldReceiveValidResponseWhenBannerFormatIsNull() throws IOException {
        // given
        final Banner bannerWithoutFormat = givenBannerWithoutFormat();

        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .banner(bannerWithoutFormat)
                .ext(impExtNode)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);

        when(mockResponse.getStatusCode()).thenReturn(202);
        when(httpClient.post(
                anyString(),
                any(MultiMap.class),
                anyString(),
                anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));
        final CommonMessage expectedCommonMessage = expectedCommonMessageBannerWithoutFormat();

        // when
        target.processEvent(event);

        // then
        verify(httpClient).post(
                eq(greenbidsAnalyticsProperties.getAnalyticsServerUrl()),
                any(MultiMap.class),
                jsonCaptor.capture(),
                eq(greenbidsAnalyticsProperties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final CommonMessage capturedCommonMessage = jacksonMapper.mapper()
                .readValue(capturedJson, CommonMessage.class);
        assertThat(capturedCommonMessage).usingRecursiveComparison()
                .ignoringFields("billingId", "greenbidsId")
                .isEqualTo(expectedCommonMessage);
        assertThat(capturedCommonMessage.getGreenbidsId()).isNotNull();
        assertThat(capturedCommonMessage.getBillingId()).isNotNull();
    }

    @Test
    public void shouldReturnValidHeadersAndTimeouts() {
        final Banner banner = givenBanner();

        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("gpid", TextNode.valueOf("gpidvalue"));
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(impExtNode)
                .banner(banner)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
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
        verify(httpClient).post(
                eq(greenbidsAnalyticsProperties.getAnalyticsServerUrl()),
                headersCaptor.capture(),
                anyString(),
                eq(greenbidsAnalyticsProperties.getTimeoutMs()));

        assertThat(result.succeeded()).isTrue();
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.CONTENT_TYPE_HEADER))
                .isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString());
        assertThat(headersCaptor.getValue().get(HttpUtil.USER_AGENT_HEADER))
                .isEqualTo(givenUserAgent());
    }

    @Test
    public void shouldFailWhenBidResponseIsNull() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(identity(), null, false);
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
    public void shouldFailOnEmptyImpExtension() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = target.processEvent(event);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("imp.ext.prebid should not be empty");
    }

    @Test
    public void shouldFailOnEncodeException() {
        // given
        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("gpid", TextNode.valueOf("gpidvalue"));
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .ext(impExtNode)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        final JacksonMapper mockJacksonMapper = mock(JacksonMapper.class);
        final ObjectMapper realObjectMapper = new ObjectMapper();
        when(mockJacksonMapper.mapper()).thenReturn(realObjectMapper);
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
        final ObjectNode impExtNode = mapper.createObjectNode();
        impExtNode.set("gpid", TextNode.valueOf("gpidvalue"));
        impExtNode.set("prebid", givenPrebidBidderParamsNode());

        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .banner(banner)
                .ext(impExtNode)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
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
        final AuctionContext auctionContext = givenAuctionContext(identity(), List.of(imp), true);
        final AuctionEvent event = AuctionEvent.builder()
                .auctionContext(auctionContext)
                .bidResponse(auctionContext.getBidResponse())
                .build();

        // when
        final Future<Void> result = target.processEvent(event);

        //then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Error decoding imp.ext.prebid: "
                        + "Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request.ExtOptions`");
    }

    private static AuctionContext givenAuctionContext(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer,
            List<Imp> imps,
            boolean includeBidResponse) {

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(givenBidRequest(request -> request, imps))
                .bidRejectionTrackers(Map.of("seat3", givenBidRejectionTracker()));

        if (includeBidResponse) {
            auctionContextBuilder.bidResponse(givenBidResponse(response -> response));
        }

        return auctionContextCustomizer.apply(auctionContextBuilder).build();
    }

    private static AuctionContext givenAuctionContextWithAnalyticsTag(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer,
            List<Imp> imps,
            boolean includeBidResponse,
            boolean includeHookExecutionContextWithAnalyticsTag) {
        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(givenBidRequest(request -> request, imps))
                .bidRejectionTrackers(Map.of("seat3", givenBidRejectionTracker()));

        if (includeHookExecutionContextWithAnalyticsTag) {
            final HookExecutionContext hookExecutionContext = givenHookExecutionContextWithAnalyticsTag();
            auctionContextBuilder.hookExecutionContext(hookExecutionContext);
        }

        if (includeBidResponse) {
            auctionContextBuilder.bidResponse(givenBidResponse(response -> response));
        }

        return auctionContextCustomizer.apply(auctionContextBuilder).build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Imp> imps) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request1")
                .imp(imps)
                .site(givenSite(site -> site))
                .device(givenDevice(device -> device))
                .ext(givenExtRequest())).build();
    }

    private static Site givenSite(UnaryOperator<Site.SiteBuilder> siteCustomizer) {
        return siteCustomizer.apply(Site.builder().domain("www.leparisien.fr")).build();
    }

    private static Device givenDevice(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        return deviceCustomizer.apply(Device.builder().ua(givenUserAgent()))
                .build();
    }

    private static String givenUserAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_6_8)"
                + "AppleWebKit/537.13 (KHTML, like Gecko) Version/5.1.7 Safari/534.57.2";
    }

    private static HookExecutionContext givenHookExecutionContextWithAnalyticsTag() {
        final ObjectNode analyticsResultNode = mapper.valueToTree(
                singletonMap(
                        "adunitcodevalue",
                        createAnalyticsResultNode()));

        final ActivityImpl activity = ActivityImpl.of(
                "greenbids-filter",
                "success",
                Collections.singletonList(
                        ResultImpl.of("success", analyticsResultNode, null)));

        final TagsImpl tags = TagsImpl.of(Collections.singletonList(activity));

        final HookExecutionOutcome hookExecutionOutcome = HookExecutionOutcome.builder()
                .hookId(HookId.of("greenbids-real-time-data", null))
                .analyticsTags(tags)
                .build();

        final GroupExecutionOutcome groupExecutionOutcome = GroupExecutionOutcome.of(
                Collections.singletonList(hookExecutionOutcome));

        final StageExecutionOutcome stageExecutionOutcome = StageExecutionOutcome.of(
                "auction-request", Collections.singletonList(groupExecutionOutcome));

        final EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes = new EnumMap<>(Stage.class);
        stageOutcomes.put(Stage.processed_auction_request, Collections.singletonList(stageExecutionOutcome));

        return HookExecutionContext.of(null, stageOutcomes);
    }

    private static BidResponse givenBidResponse(UnaryOperator<BidResponse.BidResponseBuilder> bidResponseCustomizer) {
        return bidResponseCustomizer.apply(BidResponse.builder()
                .id("response1")
                .seatbid(List.of(
                        givenSeatBid(
                                seatBid -> seatBid.seat("seat1"),
                                bid -> bid.id("bid1").price(BigDecimal.valueOf(1.5))),
                        givenSeatBid(
                                seatBid -> seatBid.seat("seat2"),
                                bid -> bid.id("bid2").price(BigDecimal.valueOf(0.5)))))
                .cur("USD")).build();
    }

    private static BidResponse givenBidResponseWithAnalyticsTag(
            UnaryOperator<BidResponse.BidResponseBuilder> bidResponseCustomizer) {
        final ObjectNode analyticsResultNode = mapper.valueToTree(
                singletonMap(
                        "adunitcodevalue",
                        createAnalyticsResultNode()));

        final ExtModulesTraceAnalyticsTags analyticsTags = ExtModulesTraceAnalyticsTags.of(
                Collections.singletonList(
                        ExtModulesTraceAnalyticsActivity.of(
                                null, null, Collections.singletonList(
                                        ExtModulesTraceAnalyticsResult.of(
                                                null, analyticsResultNode, null)))));

        final ExtModulesTraceInvocationResult invocationResult = ExtModulesTraceInvocationResult.builder()
                .hookId(HookId.of("greenbids-real-time-data", null))
                .analyticsTags(analyticsTags)
                .build();

        final ExtModulesTraceStageOutcome outcome = ExtModulesTraceStageOutcome.of(
                "auction-request", null,
                Collections.singletonList(ExtModulesTraceGroup.of(
                        null, Collections.singletonList(invocationResult))));

        final ExtModulesTraceStage stage = ExtModulesTraceStage.of(
                Stage.processed_auction_request, null,
                Collections.singletonList(outcome));

        final ExtModulesTrace modulesTrace = ExtModulesTrace.of(null, Collections.singletonList(stage));

        final ExtModules modules = ExtModules.of(null, null, modulesTrace);

        final ExtBidResponsePrebid prebid = ExtBidResponsePrebid.builder().modules(modules).build();

        final ExtBidResponse extBidResponse = ExtBidResponse.builder().prebid(prebid).build();

        return bidResponseCustomizer.apply(BidResponse.builder()
                .id("response2")
                .seatbid(List.of(
                        givenSeatBid(
                                seatBid -> seatBid.seat("seat1"),
                                bid -> bid.id("bid1").price(BigDecimal.valueOf(1.5))),
                        givenSeatBid(
                                seatBid -> seatBid.seat("seat2"),
                                bid -> bid.id("bid2").price(BigDecimal.valueOf(0.5)))))
                .cur("USD")
                .ext(extBidResponse)).build();
    }

    private static ObjectNode createAnalyticsResultNode() {
        final ObjectNode keptInAuctionNode = new ObjectNode(JsonNodeFactory.instance);
        keptInAuctionNode.put("seat1", true);
        keptInAuctionNode.put("seat2", true);
        keptInAuctionNode.put("seat3", true);

        final ObjectNode explorationResultNode = new ObjectNode(JsonNodeFactory.instance);
        explorationResultNode.put("fingerprint", "4f8d2e76-87fe-47c7-993f-d905b5fe2aa7");
        explorationResultNode.set("keptInAuction", keptInAuctionNode);
        explorationResultNode.put("isExploration", false);

        final ObjectNode analyticsResultNode = new ObjectNode(JsonNodeFactory.instance);
        analyticsResultNode.set("greenbids", explorationResultNode);
        analyticsResultNode.put("tid", "c65c165d-f4ea-4301-bb91-982ce813dd3e");

        return analyticsResultNode;
    }

    private static SeatBid givenSeatBid(UnaryOperator<SeatBid.SeatBidBuilder> seatBidCostumizer,
                                        UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return seatBidCostumizer.apply(SeatBid.builder()
                .bid(givenBids(bidCustomizers))).build();
    }

    private static List<Bid> givenBids(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return Arrays.stream(bidCustomizers)
                .map(GreenbidsAnalyticsReporterTest::givenBid)
                .toList();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()
                .impid("adunitcodevalue")
                .adm("<div>Ad Markup</div>")).build();
    }

    private static BidRejectionTracker givenBidRejectionTracker() {
        final BidRejectionTracker bidRejectionTracker = new BidRejectionTracker(
                "seat3",
                Set.of("adunitcodevalue"),
                1.0);
        bidRejectionTracker.rejectImp("imp1", BidRejectionReason.NO_BID);
        return bidRejectionTracker;
    }

    private static ObjectNode givenPrebidBidderParamsNode() {
        final ObjectNode bidderNode = mapper.createObjectNode();

        final ObjectNode seat1Params = mapper.createObjectNode()
                .put("accountId", 1001)
                .put("siteId", 267318)
                .put("zoneId", 1861698);
        bidderNode.set("seat1", seat1Params);

        final ObjectNode seat2Params = mapper.createObjectNode()
                .put("publisherId", 111)
                .put("adSlotId", 123456);
        bidderNode.set("seat2", seat2Params);

        final ObjectNode seat3Params = mapper.createObjectNode()
                .put("placementId", 222);
        bidderNode.set("seat3", seat3Params);

        final ObjectNode prebidNode = mapper.createObjectNode();
        prebidNode.set("bidder", bidderNode);

        return prebidNode;
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

    private static CommonMessage expectedCommonMessageForBanner() {
        return expectedCommonMessage(
                adUnit -> adUnit
                        .code("adunitcodevalue")
                        .unifiedCode(GreenbidsUnifiedCode.of("gpidvalue", "gpidSource"))
                        .mediaTypes(MediaTypes.of(givenExtBanner(320, 50, null), null, null))
                        .bids(expectedGreenbidBids()));
    }

    private static CommonMessage givenCommonMessageForBannerWithRtb2Imp() {
        return expectedCommonMessage(
                adUnit -> adUnit
                        .code("adunitcodevalue")
                        .unifiedCode(GreenbidsUnifiedCode.of("gpidvalue", "gpidSource"))
                        .mediaTypes(MediaTypes.of(givenExtBanner(320, 50, null), null, null))
                        .bids(expectedGreenbidBids())
                        .ortb2ImpResult(givenOrtb2Imp()));
    }

    private static CommonMessage expectedCommonMessageForVideo() {
        return expectedCommonMessage(
                adUnit -> adUnit
                        .code("adunitcodevalue")
                        .unifiedCode(GreenbidsUnifiedCode.of("adunitcodevalue", "adUnitCodeSource"))
                        .mediaTypes(MediaTypes.of(null, givenVideo(), null))
                        .bids(expectedGreenbidBids()));
    }

    private static CommonMessage expectedCommonMessageBannerWithoutFormat() {
        return expectedCommonMessage(
                adUnit -> adUnit
                        .code("adunitcodevalue")
                        .unifiedCode(GreenbidsUnifiedCode.of("adunitcodevalue", "adUnitCodeSource"))
                        .mediaTypes(MediaTypes.of(givenExtBanner(728, 90, 1), null, null))
                        .bids(expectedGreenbidBids()));
    }

    private static CommonMessage expectedCommonMessage(
            UnaryOperator<GreenbidsAdUnit.GreenbidsAdUnitBuilder>... greenbidsAdUnitCutomizers) {
        return CommonMessage.builder()
                .version("2.2.0")
                .auctionId("request1")
                .sampling(1.0)
                .pbuid("leparisien")
                .adUnits(
                        Arrays.stream(greenbidsAdUnitCutomizers)
                                .map(customizer -> customizer.apply(GreenbidsAdUnit.builder()).build())
                                .collect(Collectors.toList()))
                .auctionElapsed(0L)
                .build();
    }

    private static List<GreenbidsBid> expectedGreenbidBids() {
        final ObjectNode paramsSeat1 = mapper.createObjectNode()
                .put("accountId", 1001)
                .put("siteId", 267318)
                .put("zoneId", 1861698);

        final ObjectNode paramsSeat2 = mapper.createObjectNode()
                .put("publisherId", 111)
                .put("adSlotId", 123456);

        final ObjectNode paramsSeat3 = mapper.createObjectNode()
                .put("placementId", 222);

        return expectedGreenbidsBidsWithCustomizer(
                builder -> builder.bidder("seat2").isTimeout(false).hasBid(true)
                        .cpm(BigDecimal.valueOf(0.5)).currency("USD").params(paramsSeat2),
                builder -> builder.bidder("seat1").isTimeout(false).hasBid(true)
                        .cpm(BigDecimal.valueOf(1.5)).currency("USD").params(paramsSeat1),
                builder -> builder.bidder("seat3").isTimeout(false).hasBid(false)
                        .currency("USD").params(paramsSeat3));
    }

    private static List<GreenbidsBid> expectedGreenbidsBidsWithCustomizer(
            UnaryOperator<GreenbidsBid.GreenbidsBidBuilder>... greenbidsBidsCustomizers) {
        return Arrays.stream(greenbidsBidsCustomizers)
                .map(customizer -> customizer.apply(GreenbidsBid.builder()).build())
                .collect(Collectors.toList());
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

    private static ExtBanner givenExtBanner(Integer w, Integer h, Integer pos) {
        return ExtBanner.builder()
                .sizes(List.of(List.of(w, h)))
                .pos(pos)
                .build();
    }

    private static Video givenVideo() {
        return Video.builder()
                .pos(1)
                .plcmt(1)
                .build();
    }

    private static Ortb2ImpResult givenOrtb2Imp() {
        return Ortb2ImpResult.of(
                Ortb2ImpExtResult.of(
                        ExplorationResult.of(
                                "4f8d2e76-87fe-47c7-993f-d905b5fe2aa7",
                                Map.of("seat1", true, "seat2", true, "seat3", true),
                                false
                        ),
                        "c65c165d-f4ea-4301-bb91-982ce813dd3e"
                )
        );
    }
}
