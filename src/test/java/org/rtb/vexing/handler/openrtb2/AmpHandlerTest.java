package org.rtb.vexing.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
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
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtBidRequest;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestPrebidCache;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestTargeting;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidPrebid;
import org.rtb.vexing.settings.StoredRequestFetcher;
import org.rtb.vexing.settings.model.StoredRequestResult;
import org.rtb.vexing.validation.RequestValidator;
import org.rtb.vexing.validation.ValidationResult;

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
    private StoredRequestFetcher storedRequestFetcher;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private UidsCookieService uidsCookieService;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private MultiMap httpRequestHeaders;

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

        ampHandler = new AmpHandler(5000, 50, storedRequestFetcher, preBidRequestContextFactory, requestValidator,
                exchangeService, uidsCookieService);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new AmpHandler(1, 1, storedRequestFetcher, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, storedRequestFetcher,
                preBidRequestContextFactory, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, storedRequestFetcher,
                preBidRequestContextFactory, requestValidator, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AmpHandler(1, 1, storedRequestFetcher,
                preBidRequestContextFactory, requestValidator, exchangeService, null));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestHasNoTagId() {
        // given
        given(httpRequest.getParam(anyString())).willReturn(null);

        // when
        ampHandler.handle(routingContext);

        // then
        verifyZeroInteractions(storedRequestFetcher);
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: AMP requests require an AMP tag_id"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestCouldNotFetched() {
        // given
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(
                eq("Invalid request format: Stored request fetching failed with exception: java.lang.RuntimeException"));
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestHasErrors() {
        // given
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
                .willReturn(givenStoredRequestResultFuture(builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, null, null))))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: AMP requests require Targeting and Caching to be set"));
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsNotValid() throws JsonProcessingException {
        // given
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(any(), any()))
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
        given(storedRequestFetcher.getStoredRequestsByAmpId(eq(singleton("tagId1")), any()))
                .willReturn(givenStoredRequestResultFuture(identity()));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        final Map<String, String> targeting = new HashMap<>();
        targeting.put("key1", "value1");
        targeting.put("hb_cache_id_bidder1", "value2");
        given(exchangeService.holdAuction(any(), any(), any())).willReturn(
                givenBidResponseFuture(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder()
                        .targeting(targeting)
                        .build(), null))));

        // when
        ampHandler.handle(routingContext);

        // then
        verify(storedRequestFetcher).getStoredRequestsByAmpId(eq(singleton("tagId1")), any());
        verify(preBidRequestContextFactory).fromRequest(any(BidRequest.class), any(RoutingContext.class));
        verify(requestValidator).validate(any(BidRequest.class));
        verify(exchangeService).holdAuction(any(), any(), any());

        verify(httpResponse).putHeader("AMP-Access-Control-Allow-Source-Origin", (String) null);
        verify(httpResponse).putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{\"targeting\":{\"key1\":\"value1\",\"hb_cache_id_bidder1\":\"value2\"}}"));
    }

    private static Future<StoredRequestResult> givenStoredRequestResultFuture(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer)
            throws JsonProcessingException {

        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.of(
                                ExtRequestTargeting.of(null, 0),
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
}
