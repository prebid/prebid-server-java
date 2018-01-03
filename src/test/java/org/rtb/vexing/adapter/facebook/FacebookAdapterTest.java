package org.rtb.vexing.adapter.facebook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.channel.ConnectTimeoutException;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.facebook.model.FacebookExt;
import org.rtb.vexing.adapter.facebook.model.FacebookParams;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.Video;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class FacebookAdapterTest extends VertxTest {

    private static final String ADAPTER = "audienceNetwork";
    private static final String ENDPOINT_URL = "https://secure-endpoint.org";
    private static final String NONSECURE_ENDPOINT_URL = ENDPOINT_URL;
    private static final String USERSYNC_URL = "http://usersync.org";
    private static final String PLATFORM_ID = "100";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private UidsCookie uidsCookie;

    private FacebookAdapter adapter;
    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;

    @Before
    public void setUp() throws Exception {
        // given

        // http client returns http client request
        given(httpClient.postAbs(anyString(), any())).willReturn(httpClientRequest);
        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        bidder = givenBidderCustomizable(identity(), identity());

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // adapter
        adapter = new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, PLATFORM_ID, httpClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, PLATFORM_ID, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter("invalid_url", NONSECURE_ENDPOINT_URL, USERSYNC_URL, PLATFORM_ID,
                        httpClient))
                .withMessage("URL supplied is not valid: 'invalid_url'");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter(ENDPOINT_URL, "invalid_url", USERSYNC_URL, PLATFORM_ID,
                        httpClient))
                .withMessage("URL supplied is not valid: 'invalid_url'");
    }

    @Test
    public void creationShouldFailOnInvalidPlatformId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, "non-number",
                        httpClient))
                .withMessage("Platform ID is not valid number: 'non-number'");
    }

    @Test
    public void requestBidsShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> adapter.requestBids(null, null));
        assertThatNullPointerException().isThrownBy(() -> adapter.requestBids(bidder, null));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestWithExpectedHeaders() {
        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        verify(httpClient).postAbs(contains("secure-endpoint.org"), any());
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Content-Type")), eq("application/json;charset=utf-8"));
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Accept")), eq(new AsciiString("application/json")));
        verify(httpClientRequest).setTimeout(eq(1000L));
    }

    @Test
    public void requestBidShouldFailIfAdUnitBidHasInvalidFieldsForVideo() {
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(new HashSet<>(singletonList(MediaType.video)))
                        .video(Video.builder().build()), // no mimes
                identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("Invalid AdUnit: VIDEO media type with no video data");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfAdUnitBidHasInvalidFieldsForBanner() {
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(new HashSet<>(singletonList(MediaType.banner)))
                        .instl(0)
                        .sizes(singletonList(Format.builder().h(42).build())),
                identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("Facebook do not support banner height other than 50, 90 and 250");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(identity(), identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(null), identity())));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull()
                .returns("Facebook params section is missing", status -> status.error);
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfPlacementIdParamIsMissing() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("placementId", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("Missing placementId param");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfPlacementIdParamHasInvalidFormat() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("placementId", new TextNode("invalid-placement-id"));
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("Invalid placementId param 'invalid-placement-id'");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfNoImpsCreated() {
        bidder = givenBidderCustomizable(builder -> builder.mediaTypes(emptySet()), identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("openRTB bids need at least one Imp");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidsShouldSendBidRequestWithExpectedFields() throws IOException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(ADAPTER)
                        .adUnitCode("adUnitCode")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                paramsBuilder -> paramsBuilder
                        .placementId("pub1_place1"));

        preBidRequestContext = givenPreBidRequestContextCustomizable(
                builder -> builder
                        .timeout(1500L)
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .tid("tid")
        );

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUid");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        assertThat(bidRequest).isEqualTo(
                BidRequest.builder()
                        .id("tid")
                        .at(1)
                        .tmax(1500L)
                        .imp(singletonList(Imp.builder()
                                .id("adUnitCode")
                                .instl(1)
                                .tagid("pub1_place1")
                                .banner(Banner.builder()
                                        .w(0)
                                        .h(0)
                                        .topframe(1)
                                        .format(singletonList(Format.builder()
                                                .w(300)
                                                .h(250)
                                                .build()))
                                        .build())
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .publisher(Publisher.builder().id("pub1").build())
                                .build())
                        .device(Device.builder()
                                .ua("userAgent")
                                .ip("192.168.144.1")
                                .build())
                        .user(User.builder()
                                .buyeruid("buyerUid")
                                .build())
                        .source(Source.builder()
                                .fd(1)
                                .tid("tid")
                                .build())
                        .ext(mapper.valueToTree(FacebookExt.builder()
                                .platformid(Integer.valueOf(PLATFORM_ID)).build()))
                        .build());
    }

    @Test
    public void requestBidsShouldSendBidRequestWithAppFromPreBidRequest() throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build()).user(User.builder().build()));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getApp()).isNotNull()
                .returns("appId", from(App::getId));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithUserFromPreBidRequestIfAppPresent() throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUidFromCookie");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void requestBidsShouldSendTwoBidRequestsIfAdUnitContainsBannerAndVideoMediaTypes() throws Exception {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity()
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());

        final List<BidRequest> bidRequests = bidRequestCaptor.getAllValues().stream()
                .map(FacebookAdapterTest::toBidRequest)
                .collect(Collectors.toList());
        assertThat(bidRequests).hasSize(2);

        // check that one of the requests has imp with Banner and another one imp with Video mediaType
        assertThat(bidRequests).flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void requestBidsShouldSendMultipleBidRequestsIfMultipleAdUnitsInPreBidRequest() throws IOException {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());
        final List<BidRequest> bidRequests = bidRequestCaptor.getAllValues().stream()
                .map(FacebookAdapterTest::toBidRequest)
                .collect(Collectors.toList());
        assertThat(bidRequests).hasSize(2)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfConnectTimeoutOccurs() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new ConnectTimeoutException()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.timedOut).isTrue();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Timed out");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfTimeoutOccurs() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new TimeoutException()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.timedOut).isTrue();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Timed out");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Request exception");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Response exception");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithZeroBidsIfResponseCodeIs204() {
        // given
        givenHttpClientReturnsResponses(204, "response");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfResponseCodeIsNot200Or204() {
        // given
        givenHttpClientReturnsResponses(503, "response");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("HTTP status 503; body: response");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponses(200, "response");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).startsWith("Failed to decode");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithErrorIfBidImpIdDoesNotMatchAdUnitCode()
            throws JsonProcessingException {
        // given
        bidder = givenBidderCustomizable(builder -> builder.adUnitCode("adUnitCode"), identity());

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(),
                builder -> builder.impid("anotherAdUnitCode"));
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutErrorIfBidsArePresent() throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(ADAPTER, asList(adUnitBid, adUnitBid));

        given(httpClientRequest.exceptionHandler(any()))
                .willReturn(httpClientRequest)
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException()));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity());
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)))
                .willReturn(httpClientResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNull();
        assertThat(bidderResult.bids).hasSize(1);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithLatestErrorIfBidsAreAbsent() throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(ADAPTER, asList(adUnitBid, adUnitBid, adUnitBid));

        given(httpClientRequest.exceptionHandler(any()))
                .willReturn(httpClientRequest)
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException()))
                .willAnswer(withSelfAndPassObjectToHandler(new TimeoutException()));

        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer("response")))
                .willReturn(httpClientResponse)
                .willReturn(httpClientResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Timed out");
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithExpectedFields() throws JsonProcessingException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity()
        );

        final String bidResponse = givenBidResponseCustomizable(
                builder -> builder.id("bidResponseId"),
                builder -> builder.seat("seatId"),
                builder -> builder
                        .impid("adUnitCode")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .w(300)
                        .h(250)
        );
        givenHttpClientReturnsResponses(200, bidResponse);

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUid");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.bidder).isEqualTo(ADAPTER);
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNegative();
        assertThat(bidderResult.bidderStatus.numBids).isEqualTo(1);
        assertThat(bidderResult.bids).hasSize(1)
                .element(0).isEqualTo(org.rtb.vexing.model.response.Bid.builder()
                .code("adUnitCode")
                .price(new BigDecimal("8.43"))
                .adm("adm")
                .width(300)
                .height(250)
                .bidder(ADAPTER)
                .bidId("bidId")
                .responseTimeMs(bidderResult.bidderStatus.responseTimeMs)
                .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithZeroBidsIfEmptyBidResponse() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(builder -> builder.seatbid(null), identity(),
                identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.numBids).isNull();
        assertThat(bidderResult.bidderStatus.noBid).isTrue();
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithNoCookieIfNoFacebookUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isTrue();
        assertThat(bidderResult.bidderStatus.usersync).isNotNull();
        assertThat(defaultNamingMapper.treeToValue(bidderResult.bidderStatus.usersync, UsersyncInfo.class)).
                isEqualTo(UsersyncInfo.builder()
                        .url(USERSYNC_URL)
                        .type("redirect")
                        .supportCORS(false)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutNoCookieIfNoFacebookUidInCookieAndAppPresentInPreBidRequest()
            throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isNull();
        assertThat(bidderResult.bidderStatus.usersync).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithMultipleBidsIfMultipleAdUnitsInPreBidRequest()
            throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(ADAPTER, asList(adUnitBid, adUnitBid));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity());
        givenHttpClientReturnsResponses(200, bidResponse, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(2);
    }

    @Test
    public void requestBidShouldSendRequestToRandomEndpoint() throws IOException {
        // given
        adapter = new FacebookAdapter("https://secure-endpoint.org", "http://non-secure-endpoint.org", USERSYNC_URL,
                PLATFORM_ID, httpClient
        );
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity()));

        // when
        final List<Future<BidderResult>> futures = IntStream.range(0, 36)
                .mapToObj(value -> adapter.requestBids(bidder, preBidRequestContext))
                .collect(Collectors.toList());

        // then
        final boolean usedSecureUrl = futures.stream()
                .anyMatch(future -> future.result().bidderStatus.debug.get(0).requestUri
                        .equals("https://secure-endpoint.org"));
        assertThat(usedSecureUrl).isTrue();

        final boolean usedNonSecureUrl = futures.stream()
                .anyMatch(future -> future.result().bidderStatus.debug.get(0).requestUri
                        .equals("http://non-secure-endpoint.org"));
        assertThat(usedNonSecureUrl).isTrue();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrue() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        final String bidResponse1 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId1"),
                identity(), identity());
        final String bidResponse2 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId2"),
                identity(), identity());
        givenHttpClientReturnsResponses(200, bidResponse1, bidResponse2);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());
        final List<String> bidRequests = bidRequestCaptor.getAllValues();

        assertThat(bidderResult.bidderStatus.debug).hasSize(2).containsOnly(
                BidderDebug.builder()
                        .requestUri(ENDPOINT_URL)
                        .requestBody(bidRequests.get(0))
                        .responseBody(bidResponse1)
                        .statusCode(200)
                        .build(),
                BidderDebug.builder()
                        .requestUri(ENDPOINT_URL)
                        .requestBody(bidRequests.get(1))
                        .responseBody(bidResponse2)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutDebugIfFlagIsFalse()
            throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(false), identity());

        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.result().bidderStatus.debug).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrueAndHttpRequestFails() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.debug).hasSize(1);
        final BidderDebug bidderDebug = bidderResult.bidderStatus.debug.get(0);
        assertThat(bidderDebug.requestUri).isNotBlank();
        assertThat(bidderDebug.requestBody).isNotBlank();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrueAndResponseIsNotSuccessful() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        givenHttpClientReturnsResponses(503, "response");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.debug).hasSize(1);
        final BidderDebug bidderDebug = bidderResult.bidderStatus.debug.get(0);
        assertThat(bidderDebug.requestUri).isNotBlank();
        assertThat(bidderDebug.requestBody).isNotBlank();
        assertThat(bidderDebug.responseBody).isNotBlank();
        assertThat(bidderDebug.statusCode).isPositive();
    }

    private static Bidder givenBidderCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<FacebookParams.FacebookParamsBuilder, FacebookParams.FacebookParamsBuilder> paramsBuilderCustomizer) {

        return Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer, paramsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<FacebookParams.FacebookParamsBuilder, FacebookParams.FacebookParamsBuilder> paramsBuilderCustomizer) {

        // params
        final FacebookParams.FacebookParamsBuilder paramsBuilder = FacebookParams.builder()
                .placementId("pubId1_placement1");
        final FacebookParams.FacebookParamsBuilder paramsBuilderCustomized = paramsBuilderCustomizer
                .apply(paramsBuilder);
        final FacebookParams params = paramsBuilderCustomized.build();

        // ad unit bid
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(defaultNamingMapper.valueToTree(params))
                .mediaTypes(singleton(MediaType.banner));
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer.apply(
                adUnitBidBuilderMinimal);

        return adUnitBidBuilderCustomized.build();
    }

    private PreBidRequestContext givenPreBidRequestContextCustomizable(
            Function<PreBidRequestContext.PreBidRequestContextBuilder, PreBidRequestContext.PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer,
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder().accountId(
                "accountId");
        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderCustomized = preBidRequestBuilderCustomizer
                .apply(preBidRequestBuilderMinimal);
        final PreBidRequest preBidRequest = preBidRequestBuilderCustomized.build();

        final PreBidRequestContext.PreBidRequestContextBuilder preBidRequestContextBuilderMinimal =
                PreBidRequestContext.builder()
                        .preBidRequest(preBidRequest)
                        .uidsCookie(uidsCookie)
                        .timeout(1000L);
        final PreBidRequestContext.PreBidRequestContextBuilder preBidRequestContextBuilderCustomized =
                preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal);
        return preBidRequestContextBuilderCustomized.build();
    }

    private BidRequest captureBidRequest() throws IOException {
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(bidRequestCaptor.capture());
        return mapper.readValue(bidRequestCaptor.getValue(), BidRequest.class);
    }

    private static BidRequest toBidRequest(String bidRequest) {
        try {
            return mapper.readValue(bidRequest, BidRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void givenHttpClientReturnsResponses(int statusCode, String... bidResponses) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);

        // setup multiple answers
        BDDMockito.BDDMyOngoingStubbing<HttpClientResponse> currentStubbing =
                given(httpClientResponse.bodyHandler(any()));
        for (String bidResponse : bidResponses) {
            currentStubbing = currentStubbing.willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(bidResponse)));
        }
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);
        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClient.postAbs(anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(1)).handle(httpClientResponse);
            return httpClientRequest;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }

    private static String givenBidResponseCustomizable(
            Function<BidResponse.BidResponseBuilder, BidResponse.BidResponseBuilder> bidResponseBuilderCustomizer,
            Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> seatBidBuilderCustomizer,
            Function<com.iab.openrtb.response.Bid.BidBuilder, com.iab.openrtb.response.Bid.BidBuilder>
                    bidBuilderCustomizer) throws JsonProcessingException {

        // bid
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderMinimal = com.iab.openrtb.response.Bid.builder();
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderCustomized =
                bidBuilderCustomizer.apply(bidBuilderMinimal);
        final com.iab.openrtb.response.Bid bid = bidBuilderCustomized.build();

        // seatBid
        final SeatBid.SeatBidBuilder seatBidBuilderMinimal = SeatBid.builder().bid(singletonList(bid));
        final SeatBid.SeatBidBuilder seatBidBuilderCustomized = seatBidBuilderCustomizer.apply(seatBidBuilderMinimal);
        final SeatBid seatBid = seatBidBuilderCustomized.build();

        // bidResponse
        final BidResponse.BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder().seatbid(
                singletonList(seatBid));
        final BidResponse.BidResponseBuilder bidResponseBuilderCustomized =
                bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal);
        final BidResponse bidResponse = bidResponseBuilderCustomized.build();

        return mapper.writeValueAsString(bidResponse);
    }
}
