package org.rtb.vexing.adapter.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.BidResponse.BidResponseBuilder;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.SeatBid.SeatBidBuilder;
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
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRpTrack;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams.RubiconParamsBuilder;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargeting;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExtRp;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.AdUnitBid.AdUnitBidBuilder;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.rtb.vexing.model.request.DigiTrust;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.PreBidRequest.PreBidRequestBuilder;
import org.rtb.vexing.model.request.Video;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoParams;
import org.rtb.vexing.model.request.Sdk;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.*;

public class RubiconAdapterTest extends VertxTest {

    private static final String RUBICON_EXCHANGE = "http://rubiconproject.com/x?tk_xint=rp-pbs";
    private static final String URL = "http://example.com";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpClientRequest httpClientRequest;

    private RubiconAdapter adapter;

    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;
    @Mock
    private UidsCookie uidsCookie;

    @Before
    public void setUp() {
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
        adapter = new RubiconAdapter(RUBICON_EXCHANGE, URL, USER, PASSWORD, httpClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, USER, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(URL, URL, USER, PASSWORD, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RubiconAdapter("invalid_url", URL, USER, PASSWORD, httpClient))
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
        verify(httpClient).postAbs(contains("rubiconproject.com/x?tk_xint=rp-pbs"), any());
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Authorization")), eq("Basic dXNlcjpwYXNzd29yZA=="));
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Content-Type")), eq("application/json;charset=utf-8"));
        verify(httpClientRequest)
                .putHeader(eq(new AsciiString("Accept")), eq(new AsciiString("application/json")));
        verify(httpClientRequest).putHeader(eq(new AsciiString("User-Agent")), eq("prebid-server/1.0"));
        verify(httpClientRequest).setTimeout(eq(1000L));
    }

    @Test
    public void requestBidShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        bidder = Bidder.from(RUBICON, asList(
                givenAdUnitBidCustomizable(identity(), identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(null), identity())));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull()
                .returns("Rubicon params section is missing", status -> status.error);
        assertThat(bidderResult.bidderStatus.responseTimeMs).isNotNull();
        assertThat(bidderResult.bids).isEmpty();
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void requestBidShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("accountId", new TextNode("non-integer"));
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull().startsWith("Cannot deserialize value of type");
    }

    @Test
    public void requestBidShouldFailIfAccountIdMissingInAdUnitBidParams() {
        // given
        bidder = givenBidderCustomizable(identity(), builder -> builder.accountId(null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull().isEqualTo("Missing accountId param");
    }

    @Test
    public void requestBidShouldFailIfSiteIdMissingInAdUnitBidParams() {
        // given
        bidder = givenBidderCustomizable(identity(), builder -> builder.siteId(null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull().isEqualTo("Missing siteId param");
    }

    @Test
    public void requestBidShouldFailIfZoneIdMissingInAdUnitBidParams() {
        // given
        bidder = givenBidderCustomizable(identity(), builder -> builder.zoneId(null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull().isEqualTo("Missing zoneId param");
    }

    @Test
    public void requestBidShouldFailIfNoValidSizesInAdUnit() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.sizes(singletonList(Format.builder().w(302).h(252).build())),
                identity());

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        assertThat(bidderResultFuture.succeeded()).isTrue();
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isNotNull().isEqualTo("No valid sizes");
    }

    @Test
    public void requestBidsShouldSendBidRequestWithExpectedFields() throws IOException {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(RUBICON)
                        .adUnitCode("adUnitCode")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                identity());

        preBidRequestContext = givenPreBidRequestContextCustomizable(
                builder -> builder
                        .timeout(1500L)
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .tid("tid")
                        .sdk(Sdk.builder().source("source1").platform("platform1").version("version1").build())
                        .device(Device.builder()
                                .pxratio(new BigDecimal("4.2"))
                                .build())
        );

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUid");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        // created manually, because mapper creates Double ObjectNode instead of BigDecimal
        // for floating point numbers when capturing (production doesn't influenced)
        ObjectNode rp = mapper.createObjectNode();
        rp.set("rp", mapper.createObjectNode().put("pixelratio", new Double("4.2")));

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
                                                .track(RubiconImpExtRpTrack.builder()
                                                        .mint("prebid")
                                                        .mintVersion("source1_platform1_version1")
                                                        .build())
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
                                .pxratio(new BigDecimal("4.2"))
                                .ext(rp)
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
    public void requestBidsShouldSendBidRequestWithMobileSpecificFeatures() throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build())
                .sdk(Sdk.builder().source("source1").platform("platform1").version("version1").build())
                .device(Device.builder().pxratio(new BigDecimal("4.2")).build())
                .user(User.builder().language("language1").build()));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        assertThat(bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.track).containsOnly(RubiconImpExtRpTrack.builder()
                .mint("prebid").mintVersion("source1_platform1_version1").build());

        final Device device = bidRequest.getDevice();
        assertThat(device).isNotNull();
        assertThat(device.getExt()).isNotNull();
        final RubiconDeviceExt deviceExt = mapper.treeToValue(device.getExt(), RubiconDeviceExt.class);
        assertThat(deviceExt.rp).isNotNull();
        assertThat(deviceExt.rp.pixelratio).isEqualTo(new BigDecimal("4.2"));

        assertThat(bidRequest.getSite()).isNotNull()
                .returns(Content.builder().language("language1").build(), from(Site::getContent));
    }

    @Test
    public void requestBidsShouldSendBidRequestWithDefaultMobileSpecificFeatures() throws IOException {
        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();

        assertThat(bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.track).containsOnly(RubiconImpExtRpTrack.builder()
                .mint("prebid").mintVersion("__").build());

        final Device device = bidRequest.getDevice();
        assertThat(device).isNotNull();
        assertThat(device.getExt()).isNotNull();
        final RubiconDeviceExt deviceExt = mapper.treeToValue(device.getExt(), RubiconDeviceExt.class);
        assertThat(deviceExt.rp).isNotNull();
        assertThat(deviceExt.rp.pixelratio).isNull();

        assertThat(bidRequest.getSite()).isNotNull()
                .returns(null, from(Site::getContent));
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
                identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

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
    public void requestBidsShouldSendBidRequestWithUserFromPreBidRequestIfAppPresent() throws IOException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUidFromCookie");

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser()).isEqualTo(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void requestBidsShouldSendBidRequestWithoutInventoryAndVisitorDataIfAbsentInPreBidRequest()
            throws IOException {
        // when
        adapter.requestBids(bidder, preBidRequestContext);

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

        bidder = givenBidderCustomizable(identity(), builder -> builder.inventory(inventory).visitor(visitor));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getImp()).hasSize(1);
        assertThat(bidRequest.getImp().get(0).getExt().at("/rp/target")).isEqualTo(inventory);
        assertThat(bidRequest.getUser().getExt().at("/rp/target")).isEqualTo(visitor);
    }

    @Test
    public void requestBidsShouldSendBidRequestWithoutDigiTrustIfVisitorIsPresentAndDtIsAbsent() throws IOException {
        //given
        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        bidder = givenBidderCustomizable(identity(), builder -> builder.visitor(visitor));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser().getExt()).isNotNull();
        assertThat(bidRequest.getUser().getExt().at("/dt")).isEqualTo(MissingNode.getInstance());
        assertThat(bidRequest.getUser().getExt().at("/rp")).isNotNull();
    }

    @Test
    public void requestBidsShouldSendBidRequestWithDigiTrustFromPreBidRequest() throws IOException {
        //given
        preBidRequestContext = givenPreBidRequestContextCustomizable(
                identity(),
                builder -> builder
                        .tid("tid")
                        .digiTrust(DigiTrust.builder()
                                .id("id")
                                .keyv(123)
                                .pref(0)
                                .build()));

        //when
        adapter.requestBids(bidder, preBidRequestContext);

        //then
        final ObjectNode digiTrust = mapper.createObjectNode();
        digiTrust.set("id", new TextNode("id"));
        digiTrust.set("keyv", new IntNode(123));
        digiTrust.set("preference", new IntNode(0));

        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser().getExt().at("/dt")).isEqualTo(digiTrust);
    }

    @Test
    public void requestBidsShouldSendBidRequestWithoutDTFromPreBidRequestIfPrefIsNotZero() throws IOException {
        //given
        preBidRequestContext = givenPreBidRequestContextCustomizable(
                identity(),
                builder -> builder
                        .tid("tid")
                        .digiTrust(DigiTrust.builder()
                                .id("id")
                                .keyv(123)
                                .pref(1)
                                .build()));
        // when
        adapter.requestBids(bidder, preBidRequestContext);

        //then
        final BidRequest bidRequest = captureBidRequest();
        assertThat(bidRequest.getUser().getExt().at("/dt")).isEqualTo(MissingNode.getInstance());
    }


    @Test
    public void requestBidsShouldSendTwoBidRequestsIfAdUnitContainsBannerAndVideoMediaTypes() throws Exception {
        //given
        bidder = Bidder.from(RUBICON, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.VIDEO, MediaType.BANNER))
                                .video(Video.builder()
                                        .mimes(Collections.singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        builder -> builder.video(RubiconVideoParams.builder()
                                .skip(1)
                                .skipdelay(2)
                                .sizeId(3)
                                .build())
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(2)).end(bidRequestCaptor.capture());
        final List<BidRequest> bidRequests = bidRequestCaptor.getAllValues().stream()
                .map(RubiconAdapterTest::toBidRequest)
                .collect(Collectors.toList());
        assertThat(bidRequests).hasSize(2);
        // check that one of the requests has imp with Banner and another one imp with Video mediaType
        assertThat(bidRequests).flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void requestBidsShouldNotSendRequestIfMediaTypeIsEmpty() throws Exception {
        //given
        bidder = Bidder.from(RUBICON, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(Collections.emptySet()), identity()
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        verifyZeroInteractions(httpClientRequest);
    }

    @Test
    public void requestBidsShouldNotSendRequestWhenMediaTypeIsVideoAndMimesListIsEmpty() throws Exception {
        //given
        bidder = Bidder.from(RUBICON, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(Collections.singleton(MediaType.VIDEO))
                        .video(Video.builder()
                                .mimes(Collections.emptyList())
                                .playbackMethod(1)
                                .build()), identity()
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        verifyZeroInteractions(httpClientRequest);
    }

    @Test
    public void requestBidsShouldSendRequestWithoutVideoExtWhenMediaTypeIsVideoAndRubiconParamsVideoIsNull()
            throws Exception {
        //given
        bidder = Bidder.from(RUBICON, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(Collections.singleton(MediaType.VIDEO))
                                .video(Video.builder()
                                        .mimes(Collections.singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity()
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        adapter.requestBids(bidder, preBidRequestContext);

        // then
        final ArgumentCaptor<String> bidRequestCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest, times(1)).end(bidRequestCaptor.capture());
        final List<BidRequest> bidRequests = bidRequestCaptor.getAllValues().stream()
                .map(RubiconAdapterTest::toBidRequest)
                .collect(Collectors.toList());
        assertThat(bidRequests).hasSize(1);
        assertThat(bidRequests.get(0).getImp().get(0).getVideo().getExt()).isNull();
    }

    @Test
    public void requestBidsShouldSendMultipleBidRequestsIfMultipleAdUnitsInPreBidRequest() throws IOException {
        // given
        bidder = Bidder.from(RUBICON, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        adapter.requestBids(bidder, preBidRequestContext);

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
                builder -> builder.impid("anotherAdUnitCode"), null);
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.error).isEqualTo("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithoutErrorIfBidsArePresent()
            throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(adUnitBid, adUnitBid));

        given(httpClientRequest.exceptionHandler(any()))
                .willReturn(httpClientRequest)
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException()));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity(), null);
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
    public void requestBidsShouldReturnBidderResultWithLatestErrorIfBidsAreAbsent()
            throws JsonProcessingException {
        // given
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(adUnitBid, adUnitBid, adUnitBid));

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
                builder -> builder.bidderCode(RUBICON).bidId("bidId").adUnitCode("adUnitCode"),
                identity()
        );

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
                        .dealid("dealId"),
                singletonList(RubiconTargeting.builder()
                        .key("key")
                        .values(singletonList("value"))
                        .build())
        );
        givenHttpClientReturnsResponses(200, bidResponse);

        given(uidsCookie.uidFrom(eq(RUBICON))).willReturn("buyerUid");

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus).isNotNull();
        assertThat(bidderResult.bidderStatus.bidder).isEqualTo(RUBICON);
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
                .dealId("dealId")
                .mediaType("banner")
                .adServerTargeting(singletonMap("key", "value"))
                .bidder(RUBICON)
                .bidId("bidId")
                .responseTimeMs(bidderResult.bidderStatus.responseTimeMs)
                .build());
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithZeroBidsIfEmptyBidResponse() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(builder -> builder.seatbid(null), identity(),
                identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.numBids).isNull();
        assertThat(bidderResult.bidderStatus.noBid).isTrue();
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldFilterOutBidsWithZeroPrice() throws JsonProcessingException {
        // given
        final String bidResponse = givenBidResponseCustomizable(identity(), identity(),
                builder -> builder.price(new BigDecimal(0)), null);
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(0);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithNoCookieIfNoRubiconUidInCookieAndNoAppInPreBidRequest()
            throws IOException {
        // given
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

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
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder.app(App.builder().build()).user(User.builder().build()));

        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bidderStatus.noCookie).isNull();
        assertThat(bidderResult.bidderStatus.usersync).isNull();
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithEmptyAdTargetingIfRubiconTargetingCouldNotBeParsed()
            throws JsonProcessingException {
        // given
        final ObjectNode ext = defaultNamingMapper.createObjectNode();
        ext.set("rp", new TextNode("non-object"));
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(),
                builder -> builder.ext(ext), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(1).element(0)
                .returns(null, from(b -> b.adServerTargeting));
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithEmptyAdTargetingIfNoRubiconTargetingInBidResponse()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity(), null));

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

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
        givenHttpClientReturnsResponses(200, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

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
        final AdUnitBid adUnitBid = givenAdUnitBidCustomizable(identity(), identity());
        bidder = Bidder.from(RUBICON, asList(adUnitBid, adUnitBid));

        final String bidResponse = givenBidResponseCustomizable(identity(), identity(), identity(), null);
        givenHttpClientReturnsResponses(200, bidResponse, bidResponse);

        // when
        final Future<BidderResult> bidderResultFuture = adapter.requestBids(bidder, preBidRequestContext);

        // then
        final BidderResult bidderResult = bidderResultFuture.result();
        assertThat(bidderResult.bids).hasSize(2);
    }

    @Test
    public void requestBidsShouldReturnBidderResultWithDebugIfFlagIsTrue()
            throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        bidder = Bidder.from(RUBICON, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        final String bidResponse1 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId1"),
                identity(), identity(), null);
        final String bidResponse2 = givenBidResponseCustomizable(builder -> builder.id("bidResponseId2"),
                identity(), identity(), null);
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
    public void requestBidsShouldReturnBidderResultWithoutDebugIfFlagIsFalse()
            throws JsonProcessingException {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(false), identity());

        givenHttpClientReturnsResponses(200, givenBidResponseCustomizable(identity(), identity(), identity(), null));

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
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<RubiconParamsBuilder, RubiconParamsBuilder> rubiconParamsBuilderCustomizer) {

        return Bidder.from(RUBICON, Collections.singletonList(
                givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer, rubiconParamsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<RubiconParamsBuilder, RubiconParamsBuilder> rubiconParamsBuilderCustomizer) {

        // rubiconParams
        final RubiconParamsBuilder rubiconParamsBuilder = RubiconParams.builder()
                .accountId(2001)
                .siteId(3001)
                .zoneId(4001);
        final RubiconParamsBuilder rubiconParamsBuilderCustomized = rubiconParamsBuilderCustomizer
                .apply(rubiconParamsBuilder);
        final RubiconParams rubiconParams = rubiconParamsBuilderCustomized.build();

        // ad unit bid
        final AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(defaultNamingMapper.valueToTree(rubiconParams))
                .mediaTypes(Collections.singleton(MediaType.BANNER));
        final AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer.apply(adUnitBidBuilderMinimal);

        return adUnitBidBuilderCustomized.build();
    }

    private PreBidRequestContext givenPreBidRequestContextCustomizable(
            Function<PreBidRequestContextBuilder, PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer,
            Function<PreBidRequestBuilder, PreBidRequestBuilder> preBidRequestBuilderCustomizer) {

        final PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder().accountId("accountId");
        final PreBidRequestBuilder preBidRequestBuilderCustomized = preBidRequestBuilderCustomizer
                .apply(preBidRequestBuilderMinimal);
        final PreBidRequest preBidRequest = preBidRequestBuilderCustomized.build();

        final PreBidRequestContextBuilder preBidRequestContextBuilderMinimal =
                PreBidRequestContext.builder()
                        .preBidRequest(preBidRequest)
                        .uidsCookie(uidsCookie)
                        .timeout(1000L);
        final PreBidRequestContextBuilder preBidRequestContextBuilderCustomized =
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
            Function<BidResponseBuilder, BidResponseBuilder> bidResponseBuilderCustomizer,
            Function<SeatBidBuilder, SeatBidBuilder> seatBidBuilderCustomizer,
            Function<com.iab.openrtb.response.Bid.BidBuilder, com.iab.openrtb.response.Bid.BidBuilder>
                    bidBuilderCustomizer,
            List<RubiconTargeting> rubiconTargeting) throws JsonProcessingException {

        // bid
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderMinimal = com.iab.openrtb.response.Bid.builder()
                .price(new BigDecimal("5.67"))
                .ext(mapper.valueToTree(RubiconTargetingExt.builder()
                        .rp(RubiconTargetingExtRp.builder()
                                .targeting(rubiconTargeting)
                                .build())
                        .build()));
        final com.iab.openrtb.response.Bid.BidBuilder bidBuilderCustomized =
                bidBuilderCustomizer.apply(bidBuilderMinimal);
        final com.iab.openrtb.response.Bid bid = bidBuilderCustomized.build();

        // seatBid
        final SeatBidBuilder seatBidBuilderMinimal = SeatBid.builder().bid(singletonList(bid));
        final SeatBidBuilder seatBidBuilderCustomized = seatBidBuilderCustomizer.apply(seatBidBuilderMinimal);
        final SeatBid seatBid = seatBidBuilderCustomized.build();

        // bidResponse
        final BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder().seatbid(singletonList(seatBid));
        final BidResponseBuilder bidResponseBuilderCustomized =
                bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal);
        final BidResponse bidResponse = bidResponseBuilderCustomized.build();

        return mapper.writeValueAsString(bidResponse);
    }
}
