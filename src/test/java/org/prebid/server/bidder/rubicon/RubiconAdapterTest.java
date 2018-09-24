package org.prebid.server.bidder.rubicon;

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
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdUnitBid.AdUnitBidBuilder;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.RubiconParams;
import org.prebid.server.bidder.rubicon.proto.RubiconParams.RubiconParamsBuilder;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconRegsExt;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExt;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.Sdk;
import org.prebid.server.proto.request.Video;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.MediaType;

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

    private static final String BIDDER = "rubicon";
    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall<BidRequest, BidResponse> exchangeCall;
    private RubiconAdapter adapter;
    private RubiconUsersyncer usersyncer;

    @Before
    public void setUp() {
        adapterRequest = givenBidderCustomizable(identity(), identity());
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());
        usersyncer = new RubiconUsersyncer(USERSYNC_URL);
        adapter = new RubiconAdapter(usersyncer, ENDPOINT_URL, USER, PASSWORD);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(usersyncer, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(usersyncer, ENDPOINT_URL, null, null));
        assertThatNullPointerException().isThrownBy(() -> new RubiconAdapter(usersyncer, ENDPOINT_URL, USER, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RubiconAdapter(usersyncer, "invalid_url", USER, PASSWORD))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedHeaders() {
        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("Authorization", "Basic dXNlcjpwYXNzd29yZA=="),
                        tuple("User-Agent", "prebid-server/1.0"));
    }

    @Test
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBidCustomizable(identity(), identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(null), identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Rubicon params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", new TextNode("non-integer"));
        adapterRequest = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamAccountIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", null);
        adapterRequest = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing accountId param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamSiteIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("accountId", new IntNode(1));
        params.set("siteId", null);
        adapterRequest = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
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
        adapterRequest = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing zoneId param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsVideoAndMimesListIsEmpty() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .adUnitCode("adUnitCode1")
                                .mediaTypes(singleton(MediaType.video))
                                .video(Video.builder()
                                        .mimes(emptyList())
                                        .build()),
                        identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void makeHttpRequestsShouldFailIfNoValidAdUnits() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, emptyList());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid ad unit/imp");
    }

    @Test
    public void makeHttpRequestsShouldFailIfBannerWithoutValidSizes() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.sizes(singletonList(Format.builder().w(302).h(252).build())),
                identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid ad unit/imp");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsForBannerWithFilteredValidSizes() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.sizes(asList(Format.builder().w(302).h(252).build(),
                        Format.builder().w(300).h(250).build())),
                identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(AdapterHttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getBanner).isNotNull()
                .extracting(Banner::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class)).isNotNull()
                .extracting(RubiconBannerExt::getRp).isNotNull()
                .extracting(RubiconBannerExtRp::getSizeId).containsOnly(15);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithExpectedFields() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(BIDDER)
                        .adUnitCode("adUnitCode")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                identity());

        preBidRequestContext = givenPreBidRequestContextCustomizable(
                builder -> builder
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .timeoutMillis(1500L)
                        .tid("tid")
                        .sdk(Sdk.of("version1", "source1", "platform1"))
                        .device(Device.builder()
                                .pxratio(new BigDecimal("4.2"))
                                .build()));

        given(uidsCookie.uidFrom(eq(BIDDER))).willReturn("buyerUid");

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getPayload)
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
                                        .ext(mapper.valueToTree(RubiconBannerExt.of(
                                                RubiconBannerExtRp.of(15, null, "text/html"))))
                                        .build())
                                .ext(mapper.valueToTree(RubiconImpExt.of(RubiconImpExtRp.of(4001, null,
                                        RubiconImpExtRpTrack.of("prebid", "source1_platform1_version1")), null)))
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .publisher(Publisher.builder()
                                        .ext(mapper.valueToTree(RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                        .build())
                                .ext(mapper.valueToTree(RubiconSiteExt.of(RubiconSiteExtRp.of(3001))))
                                .build())
                        .device(Device.builder()
                                .ua("userAgent")
                                .ip("192.168.144.1")
                                .pxratio(new BigDecimal("4.2"))
                                .ext(mapper.valueToTree(RubiconDeviceExt.of(
                                        RubiconDeviceExtRp.of(new BigDecimal("4.2")))))
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
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getApp().getId())
                .containsOnly("appId");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(BIDDER))).willReturn("buyerUidFromCookie");

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getUser())
                .containsOnly(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithMobileSpecificFeatures() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build())
                .sdk(Sdk.of("version1", "source1", "platform1"))
                .device(Device.builder().pxratio(new BigDecimal("4.2")).build())
                .user(User.builder().language("language1").build()));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(RubiconImpExt::getRp).isNotNull()
                .extracting(RubiconImpExtRp::getTrack)
                .containsOnly(RubiconImpExtRpTrack.of("prebid", "source1_platform1_version1"));

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getDevice()).isNotNull()
                .extracting(Device::getExt).isNotNull()
                .extracting(objectNode -> mapper.treeToValue(objectNode, RubiconDeviceExt.class))
                .extracting(RubiconDeviceExt::getRp).isNotNull()
                .extracting(RubiconDeviceExtRp::getPixelratio)
                .containsOnly(new BigDecimal("4.2"));

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getSite()).isNotNull()
                .extracting(Site::getContent)
                .containsOnly(Content.builder().language("language1").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithDefaultMobileSpecificFeatures() {
        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(Imp::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class)).isNotNull()
                .extracting(RubiconImpExt::getRp).isNotNull()
                .extracting(RubiconImpExtRp::getTrack).containsOnly(RubiconImpExtRpTrack.of("prebid", "__"));

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getDevice()).isNotNull()
                .extracting(Device::getExt).isNotNull()
                .extracting(objectNode -> mapper.treeToValue(objectNode, RubiconDeviceExt.class))
                .extracting(RubiconDeviceExt::getRp).isNotNull()
                .extracting(RubiconDeviceExtRp::getPixelratio)
                .containsNull();

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getSite()).isNotNull()
                .extracting(Site::getContent)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithAltSizeIdsIfMoreThanOneSize() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder
                        .sizes(asList(
                                Format.builder().w(250).h(360).build(),
                                Format.builder().w(300).h(250).build(),
                                Format.builder().w(300).h(600).build())),
                identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(Imp::getBanner).isNotNull()
                .extracting(Banner::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class)).isNotNull()
                .extracting(RubiconBannerExt::getRp).isNotNull()
                .extracting(RubiconBannerExtRp::getSizeId, RubiconBannerExtRp::getAltSizeIds)
                .containsOnly(tuple(15, asList(10, 32)));
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutInventoryAndVisitorDataIfAbsentInPreBidRequest() {
        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().at("/rp/target")).containsOnly(MissingNode.getInstance());

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getUser()).isNotNull()
                .extracting(User::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithInventoryDataFromPreBidRequest() {
        // given
        final ObjectNode inventory = mapper.createObjectNode();
        inventory.set("rating", mapper.createArrayNode().add(new TextNode("5-star")));
        inventory.set("prodtype", mapper.createArrayNode().add((new TextNode("tech"))));

        adapterRequest = givenBidderCustomizable(identity(), builder -> builder.inventory(inventory));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(imp -> imp.getExt().at("/rp/target")).containsOnly(inventory);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithVisitorDataFromPreBidRequest() {
        // given
        final ObjectNode visitor = mapper.createObjectNode();
        visitor.set("ucat", mapper.createArrayNode().add(new TextNode("new")));
        visitor.set("search", mapper.createArrayNode().add((new TextNode("iphone"))));

        adapterRequest = givenBidderCustomizable(identity(), builder -> builder.visitor(visitor));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then

        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getUser()).isNotNull()
                .extracting(user -> user.getExt().at("/rp/target")).containsOnly(visitor);
    }

    @Test
    public void makeHttpRequestShouldReturnBidRequestWithConsentFromPreBidRequestUserExt() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder
                        .user(User.builder().ext(mapper.valueToTree(ExtUser.of(null, "consent", null)))
                                .build()));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getUser()).isNotNull()
                .extracting(User::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconUserExt.class))
                .extracting(RubiconUserExt::getConsent)
                .containsOnly("consent");
    }

    @Test
    public void makeHttpRequestShouldFailWithPreBidExceptionIfUserExtIsNotValidJson() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder
                        .user(User.builder().ext((ObjectNode) mapper.createObjectNode()
                                .set("consent", mapper.createObjectNode())).build()));

        // when
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize instance of `java.lang.String`");
    }

    @Test
    public void makeHttpRequestShouldReturnBidRequestWithGdprFromPreBidRequestRegsExt() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(),
                builder -> builder
                        .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(5)))));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getPayload().getRegs()).isNotNull()
                .extracting(Regs::getExt).isNotNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconRegsExt.class))
                .extracting(RubiconRegsExt::getGdpr)
                .containsOnly(5);
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoRequestsIfAdUnitContainsBannerAndVideoMediaTypes() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.getPayload().getImp())
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithMultipleRequestsIfMultipleAdUnitsInPreBidRequest() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithoutVideoExtWhenMediaTypeIsVideoAndRubiconParamsVideoIsNull
            () {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(Collections.singleton(MediaType.video))
                                .video(Video.builder()
                                        .mimes(Collections.singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp())
                .extracting(Imp::getVideo)
                .extracting(com.iab.openrtb.request.Video::getExt).containsNull();
    }

    @Test
    public void extractBidsShouldFailIfBidImpIdDoesNotMatchAdUnitCode() {
        // given
        adapterRequest = givenBidderCustomizable(builder -> builder.adUnitCode("adUnitCode"), identity());

        exchangeCall = givenExchangeCallCustomizable(identity(),
                bidResponseBuilder -> bidResponseBuilder.seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("anotherAdUnitCode")
                                .price(new BigDecimal(10)).build()))
                        .build())));

        // when and then
        assertThatThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithExpectedFields() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
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
                                        .ext(mapper.valueToTree(RubiconTargetingExt.of(RubiconTargetingExtRp.of(
                                                singletonList(RubiconTargeting.of("key", singletonList("value")))))))
                                        .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids)
                .containsExactly(org.prebid.server.proto.response.Bid.builder()
                        .code("adUnitCode")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .creativeId("crid")
                        .width(300)
                        .height(250)
                        .dealId("dealId")
                        .mediaType(MediaType.banner)
                        .adServerTargeting(singletonMap("key", "value"))
                        .bidder(BIDDER)
                        .bidId("bidId")
                        .build());
    }

    @Test
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        adapterRequest = givenBidderCustomizable(identity(), identity());

        exchangeCall = givenExchangeCallCustomizable(identity(), br -> br.seatbid(null));

        // when and then
        assertThat(adapter.extractBids(adapterRequest, exchangeCall)).isEmpty();
        assertThat(adapter.extractBids(adapterRequest, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnOnlyFirstBidBuilderFromMultipleBidsInResponse() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
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
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(org.prebid.server.proto.response.Bid::getCode)
                .containsOnly("adUnitCode1");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithZeroPriceBidsFilteredOut() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
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
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithEmptyAdTargetingIfRubiconTargetingCouldNotBeParsed() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        final ObjectNode ext = mapper.createObjectNode();
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
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(org.prebid.server.proto.response.Bid::getAdServerTargeting).containsNull();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithEmptyAdTargetingIfNoRubiconTargetingInBidResponse() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
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
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(org.prebid.server.proto.response.Bid::getAdServerTargeting).containsNull();
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithNotEmptyAdTargetingIfRubiconTargetingPresentInBidResponse() {
        // given
        adapterRequest = givenBidderCustomizable(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .price(new BigDecimal("10"))
                                        .ext(mapper.valueToTree(RubiconTargetingExt.of(RubiconTargetingExtRp.of(asList(
                                                RubiconTargeting.of("key1", singletonList("value1")),
                                                RubiconTargeting.of("key2", singletonList("value2")))))))
                                        .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1);
        assertThat(bids).flatExtracting(bid -> bid.getAdServerTargeting().entrySet())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("key1", "value1"),
                        tuple("key2", "value2"));
    }

    private static AdapterRequest givenBidderCustomizable(
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<RubiconParamsBuilder, RubiconParamsBuilder> rubiconParamsBuilderCustomizer) {

        return AdapterRequest.of(BIDDER, singletonList(
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
                .params(mapper.valueToTree(rubiconParams))
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
                        .uidsCookie(uidsCookie);
        return preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal).build();
    }

    private static ExchangeCall<BidRequest, BidResponse> givenExchangeCallCustomizable(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Function<BidResponse.BidResponseBuilder, BidResponse.BidResponseBuilder> bidResponseBuilderCustomizer) {

        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder();
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build();

        final BidResponse.BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder();
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return ExchangeCall.success(bidRequest, bidResponse, BidderDebug.builder().build());
    }
}
