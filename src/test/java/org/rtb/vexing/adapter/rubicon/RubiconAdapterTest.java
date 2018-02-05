package org.rtb.vexing.adapter.rubicon;

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
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExtRp;
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
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.AdUnitBid.AdUnitBidBuilder;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.DigiTrust;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.Sdk;
import org.rtb.vexing.model.request.Video;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class RubiconAdapterTest extends VertxTest {

    private static final String ADAPTER = "rubicon";
    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall exchangeCall;
    private RubiconAdapter adapter;

    @Before
    public void setUp() {
        bidder = givenBidderCustomizable(identity(), identity());
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());
        adapter = new RubiconAdapter(ENDPOINT_URL, USERSYNC_URL, USER, PASSWORD);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(ENDPOINT_URL, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(ENDPOINT_URL, USERSYNC_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new RubiconAdapter(ENDPOINT_URL, USERSYNC_URL, USER, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RubiconAdapter("invalid_url", USERSYNC_URL, USER, PASSWORD))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(adapter.usersyncInfo()).isEqualTo(UsersyncInfo.builder()
                .url("//usersync.org/")
                .type("redirect")
                .supportCORS(false)
                .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedHeaders() {
        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).flatExtracting(r -> r.headers.entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("Authorization", "Basic dXNlcjpwYXNzd29yZA=="),
                        tuple("User-Agent", "prebid-server/1.0"));
    }

    @Test
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(identity(), identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(null), identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Rubicon params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("accountId", new TextNode("non-integer"));
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamAccountIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing accountId param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamSiteIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", new IntNode(1));
        params.set("siteId", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing siteId param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamZoneIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", new IntNode(1));
        params.set("siteId", new IntNode(1));
        params.set("zoneId", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing zoneId param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsVideoAndMimesListIsEmpty() {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .adUnitCode("adUnitCode1")
                                .mediaTypes(singleton(MediaType.video))
                                .video(Video.builder()
                                        .mimes(emptyList())
                                        .build()),
                        identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void makeHttpRequestsShouldFailIfNoValidAdUnits() {
        // given
        bidder = Bidder.from(ADAPTER, emptyList());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid ad unit/imp");
    }

    @Test
    public void makeHttpRequestsShouldFailIfBannerWithoutValidSizes() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.sizes(singletonList(Format.builder().w(302).h(252).build())),
                identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid ad unit/imp");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsForBannerWithFilteredValidSizes() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.sizes(asList(Format.builder().w(302).h(252).build(),
                        Format.builder().w(300).h(250).build())),
                identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.bidRequest)
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getBanner).isNotNull()
                .extracting(Banner::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.sizeId).containsOnly(15);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithExpectedFields() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(ADAPTER)
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

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUid");

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.bidRequest)
                .containsOnly(BidRequest.builder()
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
                                .ext(mapper.valueToTree(RubiconDeviceExt.builder().
                                        rp(RubiconDeviceExtRp.builder().pixelratio(new BigDecimal("4.2")).build())
                                        .build()))
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
    public void makeHttpRequestsShouldReturnBidRequestsWithAppFromPreBidRequest() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build()));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getApp().getId())
                .containsOnly("appId");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUidFromCookie");

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser())
                .containsOnly(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithMobileSpecificFeatures() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build())
                .sdk(Sdk.builder().source("source1").platform("platform1").version("version1").build())
                .device(Device.builder().pxratio(new BigDecimal("4.2")).build())
                .user(User.builder().language("language1").build()));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.track).containsOnly(RubiconImpExtRpTrack.builder()
                .mint("prebid").mintVersion("source1_platform1_version1").build());

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getDevice()).isNotNull()
                .extracting(Device::getExt).isNotNull()
                .extracting(objectNode -> mapper.treeToValue(objectNode, RubiconDeviceExt.class))
                .extracting(rubiconDeviceExt -> rubiconDeviceExt.rp).isNotNull()
                .extracting(rubiconDeviceExtRp -> rubiconDeviceExtRp.pixelratio)
                .containsOnly(new BigDecimal("4.2"));

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getSite()).isNotNull()
                .extracting(Site::getContent)
                .containsOnly(Content.builder().language("language1").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithDefaultMobileSpecificFeatures() {
        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.track).containsOnly(RubiconImpExtRpTrack.builder()
                .mint("prebid").mintVersion("__").build());

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getDevice()).isNotNull()
                .extracting(Device::getExt).isNotNull()
                .extracting(objectNode -> mapper.treeToValue(objectNode, RubiconDeviceExt.class))
                .extracting(rubiconDeviceExt -> rubiconDeviceExt.rp).isNotNull()
                .extracting(rubiconDeviceExtRp -> rubiconDeviceExtRp.pixelratio)
                .containsNull();

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getSite()).isNotNull()
                .extracting(Site::getContent)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithAltSizeIdsIfMoreThanOneSize() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .sizes(asList(
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(250).h(360).build(),
                                Format.builder().w(300).h(600).build())),
                identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getBanner).isNotNull()
                .extracting(Banner::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class)).isNotNull()
                .extracting(ext -> ext.rp).isNotNull()
                .extracting(rp -> rp.sizeId, rp -> rp.altSizeIds).containsOnly(tuple(15, asList(32, 10)));
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutInventoryAndVisitorDataIfAbsentInPreBidRequest() {
        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().at("/rp/target")).containsOnly(MissingNode.getInstance());

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(User::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithInventoryAndVisitorDataFromPreBidRequest() {
        // given
        final ObjectNode inventory = mapper.createObjectNode();
        inventory.set("rating", mapper.createArrayNode().add(new TextNode("5-star")));
        inventory.set("prodtype", mapper.createArrayNode().add((new TextNode("tech"))));

        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        bidder = givenBidderCustomizable(identity(), builder -> builder.inventory(inventory).visitor(visitor));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().at("/rp/target")).containsOnly(inventory);

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(user -> user.getExt().at("/rp/target")).containsOnly(visitor);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutDigiTrustIfVisitorIsPresentAndDtIsAbsent() {
        //given
        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        bidder = givenBidderCustomizable(identity(), builder -> builder.visitor(visitor));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(User::getExt).isNotNull()
                .extracting(objectNode -> objectNode.at("/dt")).containsOnly(MissingNode.getInstance());

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(User::getExt).isNotNull()
                .extracting(objectNode -> objectNode.at("/rp")).doesNotContainNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithDigiTrustFromPreBidRequest() {
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

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        final ObjectNode digiTrust = mapper.createObjectNode();
        digiTrust.set("id", new TextNode("id"));
        digiTrust.set("keyv", new IntNode(123));
        digiTrust.set("preference", new IntNode(0));

        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(User::getExt).isNotNull()
                .extracting(objectNode -> objectNode.at("/dt")).containsOnly(digiTrust);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutDTFromPreBidRequestIfPrefIsNotZero() {
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
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.bidRequest.getUser()).isNotNull()
                .extracting(User::getExt).isNotNull()
                .extracting(objectNode -> objectNode.at("/dt")).containsOnly(MissingNode.getInstance());
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoRequestsIfAdUnitContainsBannerAndVideoMediaTypes() {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.bidRequest.getImp())
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithMultipleRequestsIfMultipleAdUnitsInPreBidRequest() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutVideoExtWhenMediaTypeIsVideoAndRubiconParamsVideoIsNull
            () {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(Collections.singleton(MediaType.video))
                                .video(Video.builder()
                                        .mimes(Collections.singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity()
                )));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.bidRequest.getImp())
                .extracting(Imp::getVideo)
                .extracting(com.iab.openrtb.request.Video::getExt).containsNull();
    }

    @Test
    public void extractBidsShouldFailIfBidImpIdDoesNotMatchAdUnitCode() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.adUnitCode("adUnitCode"), identity());

        exchangeCall = givenExchangeCallCustomizable(identity(),
                bidResponseBuilder -> bidResponseBuilder.seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("anotherAdUnitCode")
                                .price(new BigDecimal(10)).build()))
                        .build())));

        // when and then
        assertThatThrownBy(() -> adapter.extractBids(bidder, exchangeCall))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithExpectedFields() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("8.43"))
                                        .adm("adm")
                                        .crid("crid")
                                        .w(300)
                                        .h(250)
                                        .dealid("dealId")
                                        .ext(mapper.valueToTree(RubiconTargetingExt.builder()
                                                .rp(RubiconTargetingExtRp.builder()
                                                        .targeting(singletonList(RubiconTargeting.builder()
                                                                .key("key")
                                                                .values(singletonList("value"))
                                                                .build()))
                                                        .build())
                                                .build()))
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids)
                .containsExactly(org.rtb.vexing.model.response.Bid.builder()
                        .code("adUnitCode")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .creativeId("crid")
                        .width(300)
                        .height(250)
                        .dealId("dealId")
                        .mediaType(MediaType.banner)
                        .adServerTargeting(singletonMap("key", "value"))
                        .bidder(ADAPTER)
                        .bidId("bidId")
                        .build());
    }

    @Test
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        bidder = givenBidderCustomizable(identity(), identity());

        exchangeCall = givenExchangeCallCustomizable(identity(), br -> br.seatbid(null));

        // when and then
        assertThat(adapter.extractBids(bidder, exchangeCall)).isEmpty();
        assertThat(adapter.extractBids(bidder, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnOnlyFirstBidBuilderFromMultipleBidsInResponse() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        exchangeCall = givenExchangeCallCustomizable(identity(),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(asList(Bid.builder().impid("adUnitCode1").price(new BigDecimal("1.1")).build(),
                                        Bid.builder().impid("adUnitCode2").price(new BigDecimal("2.2")).build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(bid -> bid.code)
                .containsOnly("adUnitCode1");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithZeroPriceBidsFilteredOut() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("0"))
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithEmptyAdTargetingIfRubiconTargetingCouldNotBeParsed() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        final ObjectNode ext = defaultNamingMapper.createObjectNode();
        ext.set("rp", new TextNode("non-object"));

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("10"))
                                        .ext(ext)
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(bid -> bid.adServerTargeting).containsNull();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithEmptyAdTargetingIfNoRubiconTargetingInBidResponse() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("10"))
                                        .ext(null)
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(bid -> bid.adServerTargeting).containsNull();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithNotEmptyAdTargetingIfRubiconTargetingPresentInBidResponse() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("10"))
                                        .ext(mapper.valueToTree(RubiconTargetingExt.builder()
                                                .rp(RubiconTargetingExtRp.builder()
                                                        .targeting(asList(RubiconTargeting.builder()
                                                                        .key("key1")
                                                                        .values(singletonList("value1"))
                                                                        .build(),
                                                                RubiconTargeting.builder()
                                                                        .key("key2")
                                                                        .values(singletonList("value2"))
                                                                        .build()))
                                                        .build())
                                                .build()))
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1);
        assertThat(bids).flatExtracting(bid -> bid.adServerTargeting.entrySet())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("key1", "value1"),
                        tuple("key2", "value2"));
    }

    private static Bidder givenBidderCustomizable(
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<RubiconParamsBuilder, RubiconParamsBuilder> rubiconParamsBuilderCustomizer) {

        return Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer, rubiconParamsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<RubiconParamsBuilder, RubiconParamsBuilder> rubiconParamsBuilderCustomizer) {

        // params
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
                .mediaTypes(singleton(MediaType.banner));
        final AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer.apply(adUnitBidBuilderMinimal);

        return adUnitBidBuilderCustomized.build();
    }

    private PreBidRequestContext givenPreBidRequestContextCustomizable(
            Function<PreBidRequestContext.PreBidRequestContextBuilder, PreBidRequestContext
                    .PreBidRequestContextBuilder> preBidRequestContextBuilderCustomizer,
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder>
                    preBidRequestBuilderCustomizer) {

        final PreBidRequest.PreBidRequestBuilder preBidRequestBuilderMinimal = PreBidRequest.builder()
                .accountId("accountId");
        final PreBidRequest preBidRequest = preBidRequestBuilderCustomizer.apply(preBidRequestBuilderMinimal).build();

        final PreBidRequestContext.PreBidRequestContextBuilder preBidRequestContextBuilderMinimal =
                PreBidRequestContext.builder()
                        .preBidRequest(preBidRequest)
                        .uidsCookie(uidsCookie)
                        .timeout(1000L);
        return preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal).build();
    }

    private static ExchangeCall givenExchangeCallCustomizable(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Function<BidResponse.BidResponseBuilder, BidResponse.BidResponseBuilder> bidResponseBuilderCustomizer) {

        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder();
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build();

        final BidResponse.BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder();
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return ExchangeCall.success(bidRequest, bidResponse, BidderDebug.builder().build());
    }
}
