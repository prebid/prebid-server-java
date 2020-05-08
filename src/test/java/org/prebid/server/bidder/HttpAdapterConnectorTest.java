package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.Bid.BidBuilder;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.BidResponse.BidResponseBuilder;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.SeatBid.SeatBidBuilder;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdUnitBid.AdUnitBidBuilder;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.PreBidRequest.PreBidRequestBuilder;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class HttpAdapterConnectorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private HttpClient httpClient;
    private Clock clock;

    private HttpAdapterConnector httpAdapterConnector;
    @Mock
    private Adapter<?, ?> adapter;

    private Usersyncer usersyncer;
    @Mock
    private UidsCookie uidsCookie;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;

    @Before
    public void setUp() {
        willReturn(singletonList(givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());
        willReturn(new TypeReference<BidResponse>() {
        }).given(adapter).responseTypeReference();

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        adapterRequest = AdapterRequest.of(null, null);
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        httpAdapterConnector = new HttpAdapterConnector(
                httpClient, new PrivacyExtractor(jacksonMapper), clock, jacksonMapper);

        usersyncer = new Usersyncer(null, "", "", null, null, false);
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedMethod() {
        // given
        givenHttpClientReturnsResponse(200, null);

        willReturn(singletonList(givenHttpRequest(GET)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).request(eq(GET), anyString(), any(), isNull(), anyLong());
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedHeaders() {
        // given
        givenHttpClientReturnsResponse(200, null);

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("key1", "value1");
        willReturn(singletonList(AdapterHttpRequest.of(POST, "uri", null, headers)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).request(any(), anyString(), eq(headers), any(), anyLong());
    }

    @Test
    public void callShouldPerformHttpRequestsWithoutAdditionalHeadersIfTheyAreNull() {
        // given
        givenHttpClientReturnsResponse(200, null);

        willReturn(singletonList(givenHttpRequest(POST))).given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).request(any(), anyString(), isNull(), any(), anyLong());
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedTimeout() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).request(any(), anyString(), any(), any(), eq(500L));
    }

    @Test
    public void callShouldPerformHttpRequestsWithExpectedBody() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        willReturn(singletonList(AdapterHttpRequest.of(POST, "uri", givenBidRequest(b -> b.id("bidRequest1")), null)))
                .given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest).isNotNull();
        assertThat(bidRequest.getId()).isEqualTo("bidRequest1");
    }

    @Test
    public void callShouldPerformHttpRequestsWithoutBodyIfItIsNull() {
        // given
        givenHttpClientReturnsResponse(200, null);

        willReturn(singletonList(givenHttpRequest(POST))).given(adapter).makeHttpRequests(any(), any());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verify(httpClient).request(any(), anyString(), any(), isNull(), anyLong());
    }

    @Test
    public void callShouldNotPerformHttpRequestsIfAdapterReturnsEmptyHttpRequests() {
        // given
        given(adapter.makeHttpRequests(any(), any())).willReturn(emptyList());

        // when
        httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfMakeHttpRequestsFails() {
        // given
        given(adapter.makeHttpRequests(any(), any())).willThrow(new PreBidException("Make http requests exception"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.badInput("Make http requests exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Make http requests exception");
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfGlobalTimeoutAlreadyExpired() {
        // given
        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder.timeout(expiredTimeout()),
                identity());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfConnectTimeoutOccurs() {
        // given
        givenHttpClientProducesException(new ConnectTimeoutException());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
    }

    @Test
    public void callShouldSubmitTimeOutErrorToAdapterIfTimeoutOccurs() {
        // given
        givenHttpClientProducesException(new TimeoutException());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.timeout("Timed out"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Timed out");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isEqualTo(BidderError.generic("Response exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("Response exception");
    }

    @Test
    public void callShouldNotSubmitErrorToAdapterIfHttpResponseStatusCodeIs204() {
        // given
        givenHttpClientReturnsResponse(204, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).isEmpty();
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseStatusCodeIsNot200Or204() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badServerResponse("HTTP status 503; body: response"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("HTTP status 503; body: response");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseStatusCodeIs400() {
        // given
        givenHttpClientReturnsResponse(400, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badInput("HTTP status 400; body: response"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("HTTP status 400; body: response");
    }

    @Test
    public void callShouldSubmitErrorToAdapterIfHttpResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNotNull();
        assertThat(adapterResponse.getError().getMessage()).startsWith("Failed to decode");
        assertThat(adapterResponse.getError().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(adapterResponse.getBidderStatus().getError()).startsWith("Failed to decode");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void callShouldUnmarshalBodyToAdapterResponseClass() throws JsonProcessingException {
        // given
        adapterRequest = AdapterRequest.of("bidderCode1", singletonList(givenAdUnitBid(identity())));

        final Adapter<String, Object> anotherAdapter = (Adapter<String, Object>) mock(Adapter.class);
        willReturn(singletonList(givenHttpRequest())).given(anotherAdapter).makeHttpRequests(any(), any());
        willReturn(new TypeReference<CustomResponse>() {
        }).given(anotherAdapter).responseTypeReference();
        given(anotherAdapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = mapper.writeValueAsString(CustomResponse.of("url", BigDecimal.ONE));
        givenHttpClientReturnsResponse(200, bidResponse);

        // when
        httpAdapterConnector.call(anotherAdapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final ArgumentCaptor<ExchangeCall<String, Object>> exchangeCallCaptor =
                ArgumentCaptor.forClass(ExchangeCall.class);
        verify(anotherAdapter).extractBids(any(), exchangeCallCaptor.capture());
        assertThat(exchangeCallCaptor.getValue().getResponse()).isInstanceOf(CustomResponse.class);
    }

    @Test
    public void callShouldReturnBidderResultWithoutErrorIfBidsArePresent() throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponse(200, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void callShouldReturnAdapterResponseWithErrorIfErrorsOccurWhileHttpRequestForNotToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, null);

        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, bidResponse),
                HttpClientResponse.of(503, null, "error response"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNotNull();
        assertThat(adapterResponse.getError().getMessage()).startsWith("HTTP status 503; body:");
        assertThat(adapterResponse.getError().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(adapterResponse.getBidderStatus().getError()).startsWith("HTTP status 503; body:");
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutErrorIfBidsArePresentWhileHttpRequestForToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        given(adapter.tolerateErrors()).willReturn(true);

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willReturn(null);

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponses(
                HttpClientResponse.of(200, null, bidResponse),
                HttpClientResponse.of(503, null, "error response"));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void callShouldReturnAdapterResponseWithErrorIfErrorsOccurWhileExtractingForNotToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willThrow(new PreBidException("adapter extractBids exception"));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponse(200, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError())
                .isEqualTo(BidderError.badServerResponse("adapter extractBids exception"));
        assertThat(adapterResponse.getBidderStatus().getError()).isEqualTo("adapter extractBids exception");
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutErrorIfBidsArePresentWhileExtractingForToleratedErrorsAdapter()
            throws JsonProcessingException {
        // given
        willReturn(asList(givenHttpRequest(), givenHttpRequest())).given(adapter).makeHttpRequests(any(), any());

        given(adapter.tolerateErrors()).willReturn(true);

        final AdUnitBid adUnitBid = givenAdUnitBid(identity());
        adapterRequest = AdapterRequest.of("bidderCode1", asList(adUnitBid, adUnitBid));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()))
                .willThrow(new PreBidException("adapter extractBids exception"));

        final String bidResponse = givenBidResponse(identity(), identity(), singletonList(identity()));
        givenHttpClientReturnsResponse(200, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getError()).isNull();
        assertThat(adapterResponse.getBidderStatus().getError()).isNull();
        assertThat(adapterResponse.getBids()).hasSize(1);
    }

    @Test
    public void callShouldReturnAdapterResponseWithEmptyBidsIfAdUnitBidIsBannerAndSizesLengthMoreThanOne()
            throws JsonProcessingException {
        // given
        adapterRequest = AdapterRequest.of("bidderCode1", singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .sizes(asList(Format.builder().w(100).h(200).build(), Format.builder().w(100).h(200).build()))
                        .bidId("bidId1"))));

        given(adapter.extractBids(any(), any()))
                .willReturn(singletonList(org.prebid.server.proto.response.Bid.builder()
                        .code("adUnitCode1")
                        .bidId("bidId1")
                        .mediaType(MediaType.banner)));

        givenHttpClientReturnsResponse(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBids()).isEmpty();
        assertThat(adapterResponse.getBidderStatus().getNumBids()).isEqualTo(0);
    }

    @Test
    public void callShouldReturnAdapterResponseWithNoCookieIfNoAdapterUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponse(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));
        usersyncer = new Usersyncer(null, "url1", null, null, null, false);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getNoCookie()).isTrue();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isNotNull();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isEqualTo(UsersyncInfo.of("url1", null, false));
    }

    @Test
    public void callShouldReturnGdprAwareAdapterResponseWithNoCookieIfNoAdapterUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1, "1--")));
        final User user = User.builder()
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent$1").build()))
                .build();
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder.regs(regs).user(user));

        givenHttpClientReturnsResponse(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        usersyncer = new Usersyncer(null, "http://url?redir=%26gdpr%3D{{gdpr}}"
                + "%26gdpr_consent%3D{{gdpr_consent}}"
                + "%26us_privacy={{us_privacy}}",
                null, null, null, false);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getNoCookie()).isTrue();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isNotNull();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isEqualTo(UsersyncInfo.of(
                "http://url?redir=%26gdpr%3D1%26gdpr_consent%3Dconsent%241%26us_privacy=1--", null, false));
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutNoCookieIfNoAdapterUidInCookieAndAppPresentInPreBidRequest()
            throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(),
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponse(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getNoCookie()).isNull();
        assertThat(adapterResponse.getBidderStatus().getUsersync()).isNull();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrue() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        adapterRequest = AdapterRequest.of("bidderCode1", asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        final String bidResponse = givenBidResponse(builder -> builder.id("bidResponseId1"),
                identity(),
                asList(bidBuilder -> bidBuilder.impid("adUnitCode1"), bidBuilder -> bidBuilder.impid("adUnitCode2")));
        givenHttpClientReturnsResponse(200, bidResponse);

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).request(any(), anyString(), any(), bidRequestCaptor.capture(), anyLong());
        final List<String> bidRequests = bidRequestCaptor.getAllValues();

        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1).containsOnly(
                BidderDebug.builder()
                        .requestUri("uri")
                        .requestBody(bidRequests.get(0))
                        .responseBody(bidResponse)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void callShouldReturnAdapterResponseWithoutDebugIfFlagIsFalse() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(false), identity());

        givenHttpClientReturnsResponse(200,
                givenBidResponse(identity(), identity(), singletonList(identity())));

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        assertThat(adapterResponseFuture.result().getBidderStatus().getDebug()).isNull();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrueAndGlobalTimeoutAlreadyExpired() {
        // given
        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .timeout(expiredTimeout())
                        .isDebug(true),
                identity());

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1);

        final BidderDebug bidderDebug = adapterResponse.getBidderStatus().getDebug().get(0);
        assertThat(bidderDebug.getRequestUri()).isNotBlank();
        assertThat(bidderDebug.getRequestBody()).isNotBlank();
    }

    @Test
    public void callShouldReturnAdapterResponseWithDebugIfFlagIsTrueAndResponseIsNotSuccessful() {
        // given
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<AdapterResponse> adapterResponseFuture =
                httpAdapterConnector.call(adapter, usersyncer, adapterRequest, preBidRequestContext);

        // then
        final AdapterResponse adapterResponse = adapterResponseFuture.result();
        assertThat(adapterResponse.getBidderStatus().getDebug()).hasSize(1);

        final BidderDebug bidderDebug = adapterResponse.getBidderStatus().getDebug().get(0);
        assertThat(bidderDebug.getRequestUri()).isNotBlank();
        assertThat(bidderDebug.getRequestBody()).isNotBlank();
        assertThat(bidderDebug.getResponseBody()).isNotBlank();
        assertThat(bidderDebug.getStatusCode()).isPositive();
    }

    private BidRequest captureBidRequest() throws IOException {
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).request(any(), anyString(), any(), bidRequestCaptor.capture(), anyLong());
        return mapper.readValue(bidRequestCaptor.getValue(), BidRequest.class);
    }

    private AdapterHttpRequest<Object> givenHttpRequest(HttpMethod method) {
        return AdapterHttpRequest.of(method, "uri", null, null);
    }

    private static AdapterHttpRequest<BidRequest> givenHttpRequest() {
        return AdapterHttpRequest.of(POST, "uri", givenBidRequest(identity()), null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder()).build();
    }

    private static AdUnitBid givenAdUnitBid(Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        final AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .adUnitCode("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .mediaTypes(singleton(MediaType.banner));
        return adUnitBidBuilderCustomizer.apply(adUnitBidBuilderMinimal).build();
    }

    private PreBidRequestContext givenPreBidRequestContext(
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer,
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder()
                .accountId("accountId");
        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(preBidRequestBuilderMinimal).build();

        final PreBidRequestContextBuilder preBidRequestContextBuilderMinimal =
                PreBidRequestContext.builder()
                        .preBidRequest(preBidRequest)
                        .uidsCookie(uidsCookie)
                        .timeout(timeout());
        return preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal).build();
    }

    private static String givenBidResponse(
            Function<BidResponseBuilder, BidResponseBuilder> bidResponseBuilderCustomizer,
            Function<SeatBidBuilder, SeatBidBuilder> seatBidBuilderCustomizer,
            List<Function<BidBuilder, BidBuilder>> bidBuilderCustomizers) throws JsonProcessingException {

        // bid
        final BidBuilder bidBuilderMinimal = com.iab.openrtb.response.Bid.builder();
        final List<Bid> bids = bidBuilderCustomizers.stream()
                .map(bidBuilderBidBuilderFunction -> bidBuilderBidBuilderFunction.apply(bidBuilderMinimal).build())
                .collect(Collectors.toList());

        // seatBid
        final SeatBidBuilder seatBidBuilderMinimal = SeatBid.builder().bid(bids);
        final SeatBid seatBid = seatBidBuilderCustomizer.apply(seatBidBuilderMinimal).build();

        // bidResponse
        final BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder()
                .seatbid(singletonList(seatBid));
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return mapper.writeValueAsString(bidResponse);
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        given(httpClient.request(any(), anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.request(any(), anyString(), any(), any(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    private void givenHttpClientReturnsResponses(HttpClientResponse... httpClientResponses) {
        BDDMockito.BDDMyOngoingStubbing<Future<HttpClientResponse>> stubbing =
                given(httpClient.request(any(), anyString(), any(), any(), anyLong()));

        // setup multiple answers
        for (HttpClientResponse httpClientResponse : httpClientResponses) {
            stubbing = stubbing.willReturn(Future.succeededFuture(httpClientResponse));
        }
    }

    private Timeout timeout() {
        return new TimeoutFactory(clock).create(500L);
    }

    private Timeout expiredTimeout() {
        return new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class CustomResponse {

        String url;

        BigDecimal price;
    }
}
