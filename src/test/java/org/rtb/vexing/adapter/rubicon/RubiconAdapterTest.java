package org.rtb.vexing.adapter.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.MissingNode;
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
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
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
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargeting;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExtRp;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class RubiconAdapterTest extends VertxTest {

    private static final String RUBICON_EXCHANGE = "http://rubiconproject.com/x?tk_xint=rp-pbs";
    private static final String URL = "http://example.com";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String REFERER = "Referer";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String RUBICON = "rubicon";
    private static final Long HTTP_REQUEST_TIMEOUT = 250L;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private PublicSuffixList psl = new PublicSuffixListFactory().build();

    @Mock
    private HttpServerRequest preBidHttpRequest;
    private Bidder bidder;
    private PreBidRequest preBidRequestBody;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;

    private RubiconAdapter adapter;
    @Mock
    private UidsCookie uidsCookie;

    @Before
    public void setUp() {
        // given

        // http client returns http client request
        given(httpClient.post((anyInt()), anyString(), anyString(), any())).willReturn(httpClientRequest);
        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        // minimal pre-bid request
        given(preBidHttpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        preBidHttpRequest.headers().set(REFERER, "http://example.com");
        given(preBidHttpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));

        bidder = givenBidderCustomizable(identity(), identity(), identity());

        preBidRequestBody = givenPreBidRequestBodyCustomizable(identity());

        given(uidsCookie.uidFrom(anyString())).willReturn(null);

        // adapter
        adapter = new RubiconAdapter(RUBICON_EXCHANGE, URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, psl);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(null, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(URL, null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(URL, URL, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(URL, URL, USER, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, USER, PASSWORD, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, USER, PASSWORD, httpClient, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RubiconAdapter("invalid_url", URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, psl))
                .withMessage("URL supplied is not valid");
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingGivenTimeout() throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.timeoutMillis(1000L));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        assertThat(captureBidRequest().getTmax()).isEqualTo(1000L);
        verify(httpClientRequest).setTimeout(eq(1000L));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingGivenTimeoutIfNull() throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.timeoutMillis(null));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        assertThat(captureBidRequest().getTmax()).isEqualTo(HTTP_REQUEST_TIMEOUT);
        verify(httpClientRequest).setTimeout(eq(HTTP_REQUEST_TIMEOUT));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingDefaultTimeoutIfZero() throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.timeoutMillis(0L));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        assertThat(captureBidRequest().getTmax()).isEqualTo(HTTP_REQUEST_TIMEOUT);
        verify(httpClientRequest).setTimeout(eq(HTTP_REQUEST_TIMEOUT));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingDefaultTimeoutIfGreater2000() throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.timeoutMillis(5000L));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        assertThat(captureBidRequest().getTmax()).isEqualTo(HTTP_REQUEST_TIMEOUT);
        verify(httpClientRequest).setTimeout(eq(HTTP_REQUEST_TIMEOUT));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestWithExpectedHeaders() {
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        verify(httpClient, times(1)).post(anyInt(), eq("rubiconproject.com"), eq("/x?tk_xint=rp-pbs"), any());
        verify(httpClientRequest, times(1))
                .putHeader(eq(new AsciiString("Authorization")), eq("Basic dXNlcjpwYXNzd29yZA=="));
        verify(httpClientRequest, times(1))
                .putHeader(eq(new AsciiString("Content-Type")), eq("application/json;charset=utf-8"));
        verify(httpClientRequest, times(1))
                .putHeader(eq(new AsciiString("Accept")), eq(new AsciiString("application/json")));
        verify(httpClientRequest, times(1)).putHeader(eq(new AsciiString("User-Agent")), eq("prebid-server/1.0"));
        verify(httpClientRequest, times(1)).setTimeout(eq(1000L));
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingPortFromUrl() {
        adapter = new RubiconAdapter("http://rubiconproject.com:8888/x", URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, psl);

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        verify(httpClient, times(1)).post(eq(8888), anyString(), anyString(), any());
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingPort80ForHttp() {
        adapter = new RubiconAdapter(RUBICON_EXCHANGE, URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, psl);

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        verify(httpClient, times(1)).post(eq(80), anyString(), anyString(), any());
    }

    @Test
    public void requestBidsShouldMakeHttpRequestUsingPort443ForHttps() {
        adapter = new RubiconAdapter("https://rubiconproject.com/x", URL, USER, PASSWORD, httpClient, HTTP_REQUEST_TIMEOUT, psl);

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        verify(httpClient, times(1)).post(eq(443), anyString(), anyString(), any());
    }

    @Test
    public void requestBidsShouldSendBidRequestWithExpectedFields() throws IOException {
        // given
        preBidHttpRequest.headers().set(REFERER, "http://www.example.com");
        preBidHttpRequest.headers().set("User-Agent", "userAgent");
        given(preBidHttpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.144.1"));

        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder
                .tid("tid")
                .timeoutMillis(1500L));

        bidder = givenBidderCustomizable(
                builder -> builder
                        .code("adUnitCode")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                builder -> builder.bidder(RUBICON),
                builder -> builder
                        .accountId(2001)
                        .siteId(3001)
                        .zoneId(4001));

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUid");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

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
                                .banner(Banner.builder()
                                        .w(300)
                                        .h(250)
                                        .topframe(1)
                                        .format(singletonList(Format.builder()
                                                .w(300)
                                                .h(250)
                                                .build()))
                                        .ext(mapper.valueToTree(RubiconBannerExt.builder()
                                                .rp(RubiconBannerExtRp.builder()
                                                        .sizeId(15)
                                                        .mime("text/html")
                                                        .build())
                                                .build()))
                                        .build())
                                .ext(mapper.valueToTree(RubiconImpExt.builder()
                                        .rp(RubiconImpExtRp.builder()
                                                .zoneId(4001)
                                                .build())
                                        .build()))
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .publisher(Publisher.builder()
                                        .ext(mapper.valueToTree(RubiconPubExt.builder()
                                                .rp(RubiconPubExtRp.builder()
                                                        .accountId(2001)
                                                        .build())
                                                .build()))
                                        .build())
                                .ext(mapper.valueToTree(RubiconSiteExt.builder()
                                        .rp(RubiconSiteExtRp.builder()
                                                .siteId(3001)
                                                .build())
                                        .build()))
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
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder
                .app(App.builder().id("appId").build()).user(User.builder().build()));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getApp()).isNotNull()
                .returns("appId", from(App::getId));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithSecureFlagIfXForwardedProtoIsHttps() throws IOException {
        // given
        preBidHttpRequest.headers().set("X-Forwarded-Proto", "https");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1)
                .element(0)
                .returns(1, from(Imp::getSecure));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithSecureFlagIfConnectedViaSSL() throws IOException {
        // given
        given(preBidHttpRequest.scheme()).willReturn("https");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1)
                .element(0)
                .returns(1, from(Imp::getSecure));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithAltSizeIdsIfMoreThanOneSize() throws IOException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .sizes(asList(
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(250).h(360).build(),
                                Format.builder().w(300).h(600).build())),
                identity(),
                identity());

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getBanner).isNotNull()
                .extracting(Banner::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.sizeId, rp -> rp.altSizeIds).containsOnly(tuple(15, asList(32, 10)));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithSingleIpFromXForwardedFor() throws IOException {
        // given
        preBidHttpRequest.headers().set(X_FORWARDED_FOR, " 192.168.144.1 ");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getDevice()).isNotNull()
                .returns("192.168.144.1", from(Device::getIp));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithFirstIpFromXForwardedFor() throws IOException {
        // given
        preBidHttpRequest.headers().set(X_FORWARDED_FOR, " 192.168.44.1 , 192.168.144.1 , 192.168.244.1 ");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getDevice()).isNotNull()
                .returns("192.168.44.1", from(Device::getIp));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithIpFromXRealIP() throws IOException {
        // given
        preBidHttpRequest.headers().set("X-Real-IP", " 192.168.44.1 ");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getDevice()).isNotNull()
                .returns("192.168.44.1", from(Device::getIp));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithUserFromPreBidRequestIfAppPresent() throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUidFromCookie");

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void requestBidsShouldSendBidRequestWithoutInventoryAndVisitorDataIfAbsentInPreBidRequest()
            throws IOException {
        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1);
        assertThat(bidRequest.getImp().get(0).getExt().at("/rp/target")).isEqualTo(MissingNode.getInstance());
        assertThat(bidRequest.getUser().getExt()).isNull();
    }

    @Test
    public void requestBidsShouldSendBidRequestWithInventoryAndVisitorDataFromPreBidRequest() throws IOException {
        // given
        final ObjectNode inventory = mapper.createObjectNode();
        inventory.set("rating", mapper.createArrayNode().add(new TextNode("5-star")));
        inventory.set("prodtype", mapper.createArrayNode().add((new TextNode("tech"))));

        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        bidder = givenBidderCustomizable(identity(), identity(),
                builder -> builder.inventory(inventory).visitor(visitor));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1);
        assertThat(bidRequest.getImp().get(0).getExt().at("/rp/target")).isEqualTo(inventory);
        assertThat(bidRequest.getUser().getExt().at("/rp/target")).isEqualTo(visitor);
    }

    @Test
    public void requestBidsShouldSendMultipleBidRequestsIfMultipleAdUnitsInPreBidRequest() throws IOException {
        // given
        final Bid bid = givenBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(
                AdUnitBid.from(givenAdUnitCustomizable(builder -> builder.code("adUnitCode1")), bid),
                AdUnitBid.from(givenAdUnitCustomizable(builder -> builder.code("adUnitCode2")), bid)));

        // when
        adapter.requestBids(bidder, preBidRequestBody, uidsCookie, preBidHttpRequest);

        // then
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());
        final List<BidRequest> bidRequests = bidRequestCaptor.getAllValues().stream()
                .map(RubiconAdapterTest::toBidRequest)
                .collect(Collectors.toList());
        assertThat(bidRequests).hasSize(2)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithExpectedFields() throws JsonProcessingException {
        // given
        bidder = givenBidderCustomizable(
                identity(),
                builder -> builder.bidder(RUBICON).bidId("bidId"),
                identity()
        );

        final String bidResponse = givenBidResponseCustomizable(
                builder -> builder.id("bidResponseId"),
                builder -> builder.seat("seatId"),
                builder -> builder
                        .impid("impId")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .crid("crid")
                        .w(300)
                        .h(250)
                        .dealid("dealId"),
                singletonList(RubiconTargeting.builder()
                        .key("key")
                        .values(singletonList("value"))
                        .build())
        );
        givenHttpClientReturnsResponses(bidResponse);

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUid");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.bidder).isEqualTo(RUBICON);
        assertThat(bidderResult.bidderStatus.responseTime).isPositive();
        assertThat(bidderResult.bidderStatus.numBids).isEqualTo(1);
        assertThat(bidderResult.bids).hasSize(1)
                .element(0).isEqualTo(org.rtb.vexing.model.response.Bid.builder()
                .code("impId")
                .price(new BigDecimal("8.43"))
                .adm("adm")
                .creativeId("crid")
                .width(300)
                .height(250)
                .dealId("dealId")
                .adServerTargeting(singletonMap("key", "value"))
                .bidder(RUBICON)
                .bidId("bidId")
                .responseTime(bidderResult.bidderStatus.responseTime)
                .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithZeroBidsIfEmptyBidResponse() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(givenBidResponseCustomizable(builder -> builder.seatbid(null), identity(),
                identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.bidder).isEqualTo(RUBICON);
        assertThat(bidderResult.bidderStatus.responseTime).isPositive();
        assertThat(bidderResult.bidderStatus.numBids).isEqualTo(0);
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithNoCookieIfNoRubiconUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponses(givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isTrue();
        assertThat(bidderResult.bidderStatus.usersync).isNotNull();
        assertThat(defaultNamingMapper.treeToValue(bidderResult.bidderStatus.usersync, UsersyncInfo.class)).
                isEqualTo(UsersyncInfo.builder()
                        .url("http://example.com")
                        .type("redirect")
                        .supportCORS(false)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutNoCookieIfNoRubiconUidInCookieAndAppPresentInPreBidRequest()
            throws IOException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponses(givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isNull();
        assertThat(bidderResult.bidderStatus.usersync).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithEmptyAdTargetingIfNoRubiconTargetingInBidResponse()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(1).element(0)
                .returns(null, from(b -> b.adServerTargeting));
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithNotEmptyAdTargetingIfRubiconTargetingPresentInBidResponse()
            throws JsonProcessingException {
        // given
        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity(),
                asList(
                        RubiconTargeting.builder().key("key1").values(asList("value11", "value12")).build(),
                        RubiconTargeting.builder().key("key2").values(asList("value21", "value22")).build()));
        givenHttpClientReturnsResponses(bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(1);
        assertThat(bidderResult.bids.get(0).adServerTargeting).containsOnly(
                entry("key1", "value11"),
                entry("key2", "value21"));
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithMultipleBidsIfMultipleAdUnitsInPreBidRequest()
            throws JsonProcessingException {
        // given
        final Bid bid = givenBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(
                AdUnitBid.from(givenAdUnitCustomizable(identity()), bid),
                AdUnitBid.from(givenAdUnitCustomizable(identity()), bid)));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity(), null);
        givenHttpClientReturnsResponses(bidResponse, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(2);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrueInPreBidRequest()
            throws JsonProcessingException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.isDebug(true));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity(), null);
        givenHttpClientReturnsResponses(bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(1)).end(bidRequestCaptor.capture());

        assertThat(bidderResult.bidderStatus.debug).hasSize(1).containsOnly(
                BidderDebug.builder()
                        .requestUri(RUBICON_EXCHANGE)
                        .requestBody(bidRequestCaptor.getValue())
                        .responseBody(bidResponse)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutDebugIfFlagIsFalseInPreBidRequest()
            throws JsonProcessingException {
        // given
        preBidRequestBody = givenPreBidRequestBodyCustomizable(builder -> builder.isDebug(false));

        givenHttpClientReturnsResponses(givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        assertThat(bidderResultFuture.result().bidderStatus.debug).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfQueryParameterEqualTo1()
            throws JsonProcessingException {
        // given
        given(preBidHttpRequest.getParam(eq("is_debug"))).willReturn("1");

        final Bid bid = givenBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(
                AdUnitBid.from(givenAdUnitCustomizable(builder -> builder.code("adUnitCode1")), bid),
                AdUnitBid.from(givenAdUnitCustomizable(builder -> builder.code("adUnitCode2")), bid)));

        final String bidResponse1 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId1"),
                identity(), identity(), null);
        final String bidResponse2 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId2"),
                identity(), identity(), null);
        givenHttpClientReturnsResponses(bidResponse1, bidResponse2);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();

        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());
        final List<String> bidRequests = bidRequestCaptor.getAllValues();

        assertThat(bidderResult.bidderStatus.debug).hasSize(2).containsOnly(
                BidderDebug.builder()
                        .requestUri(RUBICON_EXCHANGE)
                        .requestBody(bidRequests.get(0))
                        .responseBody(bidResponse1)
                        .statusCode(200)
                        .build(),
                BidderDebug.builder()
                        .requestUri(RUBICON_EXCHANGE)
                        .requestBody(bidRequests.get(1))
                        .responseBody(bidResponse2)
                        .statusCode(200)
                        .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutDebugIfQueryParameterNotEqualTo1()
            throws JsonProcessingException {
        // given
        given(preBidHttpRequest.getParam(eq("is_debug"))).willReturn("2");

        givenHttpClientReturnsResponses(givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestBody, uidsCookie,
                preBidHttpRequest);

        // then
        assertThat(bidderResultFuture.result().bidderStatus.debug).isNull();
    }

    private static Bidder givenBidderCustomizable(
            Function<AdUnit.AdUnitBuilder, AdUnit.AdUnitBuilder> adUnitBuilderCustomizer,
            Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilderCustomizer,
            Function<RubiconParams.RubiconParamsBuilder, RubiconParams.RubiconParamsBuilder>
                    rubiconParamsBuilderCustomizer) {

        final AdUnit adUnit = givenAdUnitCustomizable(adUnitBuilderCustomizer);
        final Bid bid = givenBidCustomizable(bidBuilderCustomizer, rubiconParamsBuilderCustomizer);

        return Bidder.from(RUBICON, Collections.singletonList(AdUnitBid.from(adUnit, bid)));
    }

    private static AdUnit givenAdUnitCustomizable(Function<AdUnit.AdUnitBuilder, AdUnit.AdUnitBuilder>
                                                          adUnitBuilderCustomizer) {
        final AdUnit.AdUnitBuilder adUnitBuilderMinimal = AdUnit.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()));
        final AdUnit.AdUnitBuilder adUnitBuilderCustomized = adUnitBuilderCustomizer.apply(adUnitBuilderMinimal);

        return adUnitBuilderCustomized.build();
    }

    private static Bid givenBidCustomizable(
            Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilderCustomizer,
            Function<RubiconParams.RubiconParamsBuilder, RubiconParams.RubiconParamsBuilder>
                    rubiconParamsBuilderCustomizer) {

        // rubiconParams
        final RubiconParams.RubiconParamsBuilder rubiconParamsBuilder = RubiconParams.builder();
        final RubiconParams.RubiconParamsBuilder rubiconParamsBuilderCustomized = rubiconParamsBuilderCustomizer
                .apply(rubiconParamsBuilder);
        final RubiconParams rubiconParams = rubiconParamsBuilderCustomized.build();

        // bid
        final Bid.BidBuilder bidBuilderMinimal = Bid.builder().params(defaultNamingMapper.valueToTree(rubiconParams));
        final Bid.BidBuilder bidBuilderCustomized = bidBuilderCustomizer.apply(bidBuilderMinimal);

        return bidBuilderCustomized.build();
    }

    private static PreBidRequest givenPreBidRequestBodyCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {

        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder()
                .timeoutMillis(1000L);
        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderCustomized = preBidRequestBuilderCustomizer
                .apply(preBidRequestBuilderMinimal);
        return preBidRequestBuilderCustomized.build();
    }

    private BidRequest captureBidRequest() throws IOException {
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(1)).end(bidRequestCaptor.capture());
        return mapper.readValue(bidRequestCaptor.getValue(), BidRequest.class);
    }

    private static BidRequest toBidRequest(String bidRequest) {
        try {
            return mapper.readValue(bidRequest, BidRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void givenHttpClientReturnsResponses(String... bidResponses) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClient.post(anyInt(), anyString(), anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(200);

        // setup multiple answers
        BDDMockito.BDDMyOngoingStubbing<HttpClientResponse> currentStubbing =
                given(httpClientResponse.bodyHandler(any()));
        for (String bidResponse : bidResponses) {
            currentStubbing = currentStubbing.willAnswer(withSelfAndPassBodyToHandler(bidResponse));
        }
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(3)).handle(httpClientResponse);
            return httpClientRequest;
        };
    }

    @SuppressWarnings("unchecked")
    private static Answer<Object> withSelfAndPassBodyToHandler(String bidResponse) {
        return inv -> {
            ((Handler<Buffer>) inv.getArgument(0)).handle(Buffer.buffer(bidResponse));
            return inv.getMock();
        };
    }

    private static String givenBidResponseCustomizable(
            Function<BidResponse.BidResponseBuilder, BidResponse.BidResponseBuilder> bidResponseBuilderCustomizer,
            Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> seatBidBuilderCustomizer,
            Function<com.iab.openrtb.response.Bid.BidBuilder, com.iab.openrtb.response.Bid.BidBuilder>
                    bidBuilderCustomizer,
            List<RubiconTargeting> rubiconTargeting) throws JsonProcessingException {

        // bid
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderMinimal = com.iab.openrtb.response.Bid.builder()
                .ext(mapper.valueToTree(RubiconTargetingExt.builder()
                        .rp(RubiconTargetingExtRp.builder()
                                .targeting(rubiconTargeting)
                                .build())
                        .build()));
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderCustomized =
                bidBuilderCustomizer.apply(bidBuilderMinimal);
        final com.iab.openrtb.response.Bid bid = bidBuilderCustomized.build();

        // seatBid
        final SeatBid.SeatBidBuilder seatBidBuilderMinimal = SeatBid.builder().bid(singletonList(bid));
        final SeatBid.SeatBidBuilder seatBidBuilderCustomized = seatBidBuilderCustomizer.apply(seatBidBuilderMinimal);
        final SeatBid seatBid = seatBidBuilderCustomized.build();

        // bidResponse
        final BidResponse.BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder()
                .seatbid(singletonList(seatBid));
        final BidResponse.BidResponseBuilder bidResponseBuilderCustomized =
                bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal);
        final BidResponse bidResponse = bidResponseBuilderCustomized.build();

        return mapper.writeValueAsString(bidResponse);
    }
}
