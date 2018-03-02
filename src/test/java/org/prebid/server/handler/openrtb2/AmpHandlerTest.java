package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredRequestResult;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AmpHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private Metrics metrics;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private MultiMap httpRequestHeaders;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private UidsCookie uidsCookie;

    private AmpHandler ampHandler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(httpRequestHeaders);
        given(httpRequest.getParam(anyString())).willReturn("tagId1");

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.putHeader(anyString(), eq((String) null))).willReturn(httpResponse);
        given(httpResponse.putHeader(anyString(), anyString())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(uidsCookieService.parseFromRequest(routingContext)).willReturn(uidsCookie);

        ampHandler = new AmpHandler(5000, 50, applicationSettings, preBidRequestContextFactory, requestValidator,
                exchangeService, uidsCookieService, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AmpHandler(1, 1, applicationSettings, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, applicationSettings,
                preBidRequestContextFactory, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, applicationSettings,
                preBidRequestContextFactory, requestValidator, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, applicationSettings,
                preBidRequestContextFactory, requestValidator, exchangeService, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, applicationSettings,
                preBidRequestContextFactory, requestValidator, exchangeService, uidsCookieService, null));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasNoTagId() {
        // given
        given(httpRequest.getParam(anyString())).willReturn(null);

        // when
        ampHandler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings);
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: AMP requests require an AMP tag_id"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestCouldNotFetched() {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq(
                "Invalid request format: Stored request fetching failed with exception: java.lang.RuntimeException"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestHasErrors() {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error1"))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: error1"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestCouldNotBeParsed() {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), emptyList())));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Failed to decode: null"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestHasNoImp() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder
                        .imp(emptyList())));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(
                eq("Invalid request format: AMP tag_id 'tagId1' does not include an Imp object. One id required"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestHasMoreThenOneImp() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder
                        .imp(asList(Imp.builder().build(), Imp.builder().build()))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(
                eq("Invalid request format: AMP tag_id 'tagId1' includes multiple Imp objects. We must have only one"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestHasNoExt() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder.ext(null)));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: AMP requests require Ext to be set"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestExtCouldNotBeParsed() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("non-ExtBidRequest"));
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder.ext(ext)));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(startsWith("Invalid request format: Error decoding bidRequest.ext:"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestExtHasNoTargetingAndCaching()
            throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, null))))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: AMP requests require Targeting and Caching to be set"));
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsNotValid() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(asList("error1", "error2")));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: error1\nInvalid request format: error2"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(exchangeService.holdAuction(any(), any(), any())).willThrow(new RuntimeException("Unexpected exception"));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfCannotExtractBidTargeting() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("non-ExtBidRequest"));
        given(exchangeService.holdAuction(any(), any(), any())).willReturn(givenBidResponseFuture(ext));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(
                startsWith("Critical error while running the auction: Critical error while unpacking AMP targets:"));
    }

    @Test
    public void shouldRespondWithExpectedResponse() throws JsonProcessingException {
        // given
        given(applicationSettings.getStoredRequestsByAmpId(eq(singleton("tagId1")), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                givenBidResponseFuture(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, targeting, null), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(applicationSettings).getStoredRequestsByAmpId(eq(singleton("tagId1")), any());
        verify(preBidRequestContextFactory).fromRequest(any(BidRequest.class), any(RoutingContext.class));
        verify(requestValidator).validate(any(BidRequest.class));
        verify(exchangeService).holdAuction(any(), any(), any());

        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", (String) null);
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"));
    }

    @Test
    public void shouldIncrementAmpRequestMetrics() throws JsonProcessingException {
        // given
        givenMocksForMetricSupport();
        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder()
                .app(App.builder().build()).build());

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.amp_requests));
        verify(metrics).incCounter(eq(MetricName.app_requests));
    }

    @Test
    public void shouldIncrementNoCookieMetrics() throws JsonProcessingException {
        // given
        givenMocksForMetricSupport();
        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());
        given(uidsCookie.hasLiveUids()).willReturn(true);

        httpRequest.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
                "AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7");

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.safari_requests));
        verify(metrics).incCounter(eq(MetricName.safari_no_cookie_requests));
        verify(metrics).incCounter(eq(MetricName.amp_no_cookie));
    }

    @Test
    public void shouldIncrementErrorRequestMetrics() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("invalid"));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.error_requests));
    }

    private static Future<StoredRequestResult> givenStoredRequestResultFuture(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer)
            throws JsonProcessingException {

        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.of(
                                null,
                                ExtRequestTargeting.of(null),
                                null,
                                ExtRequestPrebidCache.of(mapper.createObjectNode())))));

        final Map<String, String> storedIdToJson = new HashMap<>(1);
        storedIdToJson.put("tagId1",
                mapper.writeValueAsString(bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build()));

        return Future.succeededFuture(StoredRequestResult.of(storedIdToJson, emptyList()));
    }

    private static Future<BidResponse> givenBidResponseFuture(ObjectNode ext) {
        return Future.succeededFuture(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .ext(ext)
                                .build()))
                        .build()))
                .build());
    }

    private void givenMocksForMetricSupport() throws JsonProcessingException {
        given(applicationSettings.getStoredRequestsByAmpId(eq(singleton("tagId1")), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                givenBidResponseFuture(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(null, targeting, null), null))));
    }
}
