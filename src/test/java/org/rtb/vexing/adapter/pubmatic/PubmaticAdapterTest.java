package org.rtb.vexing.adapter.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.rtb.vexing.adapter.pubmatic.model.PubmaticParams;
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
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class PubmaticAdapterTest extends VertxTest {

    private static final String ADAPTER = "Pubmatic";
    private static final String UID_COOKIE = "pubmatic";
    private static final String ENDPOINT_URL = "http://exchange.org";
    private static final String USERSYNC_URL = "//usersync.org";
    private static final String EXTERNAL_URL = "http://external.org";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private UidsCookie uidsCookie;

    private PubmaticAdapter adapter;
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

        bidder = givenBidderCustomizable(identity());

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // adapter
        adapter = new PubmaticAdapter(ENDPOINT_URL, USERSYNC_URL, EXTERNAL_URL, httpClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new PubmaticAdapter(null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new PubmaticAdapter(ENDPOINT_URL, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new PubmaticAdapter(ENDPOINT_URL, USERSYNC_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new PubmaticAdapter(ENDPOINT_URL, USERSYNC_URL, EXTERNAL_URL, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PubmaticAdapter("invalid_url", USERSYNC_URL, EXTERNAL_URL, httpClient))
                .withMessage("URL supplied is not valid");
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
        verify(httpClient).postAbs(eq(ENDPOINT_URL), any());
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Content-Type")), eq("application/json;charset=utf-8"));
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Accept")), eq(new AsciiString("application/json")));
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Set-Cookie")), eq("KADUSERCOOKIE="));
        verify(httpClientRequest).setTimeout(eq(1000L));
    }

    @Test
    public void requestBidsShouldFailIfNoImpsCreated() {
        bidder = givenBidderCustomizable(builder -> builder.mediaTypes(emptySet()));

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
    public void requestBidsShouldFailIfNoAtLeastOneValidAdunitBidParams() {
        bidder = givenBidderCustomizable(
                builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of(null, null))));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .startsWith("Incorrect adSlot / Publisher param");
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidsShouldSendBidRequestWithNotModifiedImpIfInvalidParams() throws IOException {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of(null, null)))),
                givenAdUnitBidCustomizable(builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of("publisherID", null)))),
                givenAdUnitBidCustomizable(builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of("publisherID", "slot42")))),
                givenAdUnitBidCustomizable(builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of("publisherID", "slot42@200")))),
                givenAdUnitBidCustomizable(builder -> builder.params(defaultNamingMapper
                        .valueToTree(PubmaticParams.of("publisherID", "slot42@200xNonNumber"))))
        ));

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn("buyerUid");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        assertThat(bidRequest.getImp()).hasSize(6);

        assertThat(bidRequest.getImp()).extracting(Imp::getTagid)
                .containsOnly("slot1", null, null, null, null, null);

        final List formats = singletonList(Format.builder().w(480).h(320).build());
        assertThat(bidRequest.getImp()).extracting(Imp::getBanner).extracting(Banner::getFormat)
                .containsOnly(null, formats, formats, formats, formats, formats);

        assertThat(bidRequest.getImp()).extracting(Imp::getBanner).extracting(Banner::getW)
                .containsOnly(300, 480, 480, 480, 480, 480);

        assertThat(bidRequest.getImp()).extracting(Imp::getBanner).extracting(Banner::getH)
                .containsOnly(250, 320, 320, 320, 320, 320);
    }

    @Test
    public void requestBidsShouldTolerateAdSlotExtraSuffix() throws IOException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .params(defaultNamingMapper
                                .valueToTree(PubmaticParams.of("publisherID", "slot42@200x150:zzz"))));

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn("buyerUid");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest).isNotNull();

        verify(httpClient).postAbs(anyString(), any());
    }

    @Test
    public void requestBidsShouldSendBidRequestWithExpectedFields() throws IOException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(ADAPTER)
                        .adUnitCode("adUnitCode1")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build()))
                        .params(defaultNamingMapper
                                .valueToTree(PubmaticParams.of("publisherID", "slot42@200x150:zzz"))));

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

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn("buyerUid");

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
                                .id("adUnitCode1")
                                .instl(1)
                                .tagid("slot42")
                                .banner(Banner.builder()
                                        .w(200)
                                        .h(150)
                                        .topframe(1)
                                        .build())
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .publisher(Publisher.builder().id("publisherID").domain("example.com").build())
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

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn("buyerUidFromCookie");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void requestBidsShouldSendOneBidRequestIfAdUnitContainsBannerAndVideoMediaTypes() throws Exception {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                        .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                        .video(Video.builder()
                                .mimes(singletonList("Mime1"))
                                .playbackMethod(1)
                                .build()))));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        assertThat(bidRequest).isNotNull();
        // check that one of the requests has imp with Banner and imp with Video mediaType
        assertThat(bidRequest.getImp())
                .containsOnly(
                        Imp.builder()
                                .video(com.iab.openrtb.request.Video.builder().w(480).h(320)
                                        .mimes(singletonList("Mime1")).playbackmethod(singletonList(1)).build())
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().w(300).h(250).build())
                                .tagid("slot1")
                                .build()
                );
    }

    @Test
    public void requestBidsShouldNotSendRequestWhenMediaTypeIsVideoAndMimesListIsEmpty() {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(emptyList()).build()))));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        verifyZeroInteractions(httpClientRequest);

        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull()
                .isEqualTo("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void requestBidsShouldSendOneBidRequestsIfMultipleAdUnitsInPreBidRequest() throws IOException {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"))));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(2)
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
        assertThat(bidderResult.bidderStatus.error).isEqualTo("HTTP status 503");
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
        bidder = givenBidderCustomizable(builder -> builder.adUnitCode("adUnitCode"));

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
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity());
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
    public void requestBidsShouldReturnBidderResultWithExpectedFields() throws JsonProcessingException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"));

        final String bidResponse = givenBidResponseCustomizable(
                builder -> builder.id("bidResponseId"),
                builder -> builder.seat("seatId"),
                builder -> builder
                        .impid("adUnitCode")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .crid("crid")
                        .w(300)
                        .h(250)
                        .dealid("dealId")
        );
        givenHttpClientReturnsResponses(200, bidResponse);

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
                .creativeId("crid")
                .width(300)
                .height(250)
                .bidder(ADAPTER)
                .bidId("bidId")
                .dealId("dealId")
                .responseTimeMs(bidderResult.bidderStatus.responseTimeMs)
                .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithZeroBidsIfEmptyBidResponse() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(200,
                givenBidResponseCustomizable(builder -> builder.seatbid(null), identity(), identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.numBids).isNull();
        assertThat(bidderResult.bidderStatus.noBid).isTrue();
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithNoCookieIfNoPubmaticUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponses(200,
                givenBidResponseCustomizable(identity(), identity(), identity()));

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn(null);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isTrue();
        assertThat(bidderResult.bidderStatus.usersync).isNotNull();
        assertThat(defaultNamingMapper.treeToValue(bidderResult.bidderStatus.usersync, UsersyncInfo.class)).
                isEqualTo(UsersyncInfo.builder()
                        .url("//usersync.orghttp%3A%2F%2Fexternal.org%2Fsetuid%3Fbidder%3Dpubmatic%26uid%3D")
                        .type("iframe")
                        .supportCORS(false)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutNoCookieIfNoPubmaticUidInCookieAndAppPresentInPreBidRequest()
            throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponses(200,
                givenBidResponseCustomizable(identity(), identity(), identity()));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isNull();
        assertThat(bidderResult.bidderStatus.usersync).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithOneBidIfMultipleAdUnitsInPreBidRequest()
            throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity());
        bidder = Bidder.from(ADAPTER, asList(adUnitBid, adUnitBid));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity());
        givenHttpClientReturnsResponses(200, bidResponse, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(1);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrue() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"))));

        final String bidResponse = givenBidResponseCustomizable(builder -> builder.id("bidResponseId1"),
                identity(),
                bidBuilder -> bidBuilder.impid("adUnitCode1"));
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(bidRequestCaptor.capture());
        final List<String> bidRequests = bidRequestCaptor.getAllValues();

        assertThat(bidderResult.bidderStatus.debug).hasSize(1).containsOnly(
                BidderDebug.builder()
                        .requestUri(ENDPOINT_URL)
                        .requestBody(bidRequests.get(0))
                        .responseBody(bidResponse)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutDebugIfFlagIsFalse() throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(false), identity());

        givenHttpClientReturnsResponses(200,
                givenBidResponseCustomizable(identity(), identity(), identity()));

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
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        return Bidder.from(ADAPTER, singletonList(givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(480).h(320).build()))
                .params(defaultNamingMapper.valueToTree(PubmaticParams.of("publisherId1", "slot1@300x250:zzz")))
                .mediaTypes(singleton(MediaType.banner));

        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer
                .apply(adUnitBidBuilderMinimal);

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
