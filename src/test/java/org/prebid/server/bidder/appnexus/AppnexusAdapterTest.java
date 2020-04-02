package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
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
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.Video;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.MediaType;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.from;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class AppnexusAdapterTest extends VertxTest {

    private static final String BIDDER = "appnexus";
    private static final String COOKIE_FAMILY = "adnxs";
    private static final String ENDPOINT_URL = "http://endpoint.org/";
    private static final Integer BANNER_TYPE = 0;
    private static final Integer VIDEO_TYPE = 1;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall<BidRequest, BidResponse> exchangeCall;
    private AppnexusAdapter adapter;

    @Before
    public void setUp() {
        adapterRequest = givenBidder(identity(), identity());
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());
        adapter = new AppnexusAdapter(COOKIE_FAMILY, ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AppnexusAdapter(null, null, jacksonMapper));
        assertThatNullPointerException().isThrownBy(() -> new AppnexusAdapter(COOKIE_FAMILY, null, jacksonMapper));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AppnexusAdapter(COOKIE_FAMILY, "invalid_url", jacksonMapper))
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
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedEndpointUrl() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(identity(), params -> params.invCode("invCode1").member("member1"))));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getUri).containsOnly("http://endpoint.org/?member_id=member1");
    }

    @Test
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(identity(), identity()),
                givenAdUnitBid(builder -> builder.params(null), identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Appnexus params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("placement_id", new TextNode("non-integer"));
        adapterRequest = givenBidder(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfPlacementOrMemberWithInvcodeMissingInAdUnitBidParams() {
        // given
        adapterRequest = givenBidder(identity(), builder -> builder.placementId(null));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("No placement or member+invcode provided");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsEmpty() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
                                .adUnitCode("adUnitCode1")
                                .mediaTypes(emptySet()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsVideoAndMimesListIsEmpty() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
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
    public void makeHttpRequestsShouldReturnRequestsWithExpectedFields() {
        // given
        adapterRequest = givenBidder(
                builder -> builder
                        .bidderCode(BIDDER)
                        .adUnitCode("adUnitCode1")
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                appnexusParamsBuilder -> appnexusParamsBuilder
                        .keywords(singletonList(AppnexusKeyVal.of("k1", singletonList("v1"))))
                        .trafficSourceCode("<src-code/>"));

        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .tid("tid1")
                        .user(User.builder()
                                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                        .timeoutMillis(1500L)
                        .device(Device.builder()
                                .pxratio(new BigDecimal("4.2"))
                                .build()));

        given(uidsCookie.uidFrom(eq(COOKIE_FAMILY))).willReturn("buyerUid");

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getPayload)
                .containsOnly(BidRequest.builder()
                        .id("tid1")
                        .at(1)
                        .tmax(1500L)
                        .imp(singletonList(Imp.builder()
                                .id("adUnitCode1")
                                .instl(1)
                                .tagid("30011")
                                .banner(Banner.builder()
                                        .w(300)
                                        .h(250)
                                        .topframe(1)
                                        .format(singletonList(Format.builder()
                                                .w(300)
                                                .h(250)
                                                .build()))
                                        .build())
                                .ext(mapper.valueToTree(AppnexusImpExt.of(
                                        AppnexusImpExtAppnexus.of(9848285, "k1=v1", "<src-code/>", null, null))))
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .build())
                        .device(Device.builder()
                                .ua("userAgent")
                                .ip("192.168.144.1")
                                .pxratio(new BigDecimal("4.2"))
                                .build())
                        .user(User.builder()
                                .buyeruid("buyerUid")
                                .id("buyerUid")
                                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null))))
                        .source(Source.builder()
                                .fd(1)
                                .tid("tid1")
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAppFromPreBidRequest() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().id("appId").build()).user(User.builder().build()));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.getPayload().getApp().getId())
                .containsOnly("appId");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(BIDDER))).willReturn("buyerUidFromCookie");

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.getPayload().getUser())
                .containsOnly(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithOneRequestWithOneImpIfAdUnitContainsBannerAndVideoMediaTypes() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp())
                .containsOnly(
                        Imp.builder()
                                .banner(Banner.builder().w(300).h(250).format(
                                        singletonList(Format.builder().w(300).h(250).build())).build())
                                .video(com.iab.openrtb.request.Video.builder().w(300).h(250).mimes(
                                        singletonList("Mime")).playbackmethod(singletonList(1)).build())
                                .tagid("30011")
                                .ext(mapper.valueToTree(AppnexusImpExt.of(
                                        AppnexusImpExtAppnexus.of(9848285, null, null, null, null))))
                                .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithOneRequestIfMultipleAdUnitsInPreBidRequest() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void makeHttpRequestsShouldHonorLegacyParams() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(identity(), params -> params
                        .placementId(null)
                        .legacyPlacementId(101)
                        .invCode(null)
                        .legacyInvCode("legacyInvCode1")
                        .trafficSourceCode(null)
                        .legacyTrafficSourceCode("legacyTrafficSourceCode1"))));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(1)
                .extracting(Imp::getTagid, Imp::getExt).containsOnly(
                tuple(
                        "legacyInvCode1",
                        mapper.valueToTree(AppnexusImpExt.of(
                                AppnexusImpExtAppnexus.of(101, null, "legacyTrafficSourceCode1", null, null)))));
    }

    @Test
    public void extractBidsShouldFailIfBidImpIdDoesNotMatchAdUnitCode() {
        // given
        adapterRequest = givenBidder(builder -> builder.adUnitCode("adUnitCode"), identity());

        exchangeCall = givenExchangeCall(identity(),
                bidResponseBuilder -> bidResponseBuilder.seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("anotherAdUnitCode").build()))
                        .build())));

        // when and then
        assertThatThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithExpectedFields() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
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
                                        .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                AppnexusBidExtAppnexus.builder().bidAdType(BANNER_TYPE).build())))
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
                        .bidder(BIDDER)
                        .bidId("bidId")
                        .mediaType(MediaType.banner)
                        .build());
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithBidMediaTypeAsVideo() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                AppnexusBidExtAppnexus.builder().bidAdType(VIDEO_TYPE).build())))
                                        .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).element(0)
                .returns(MediaType.video, from(org.prebid.server.proto.response.Bid::getMediaType));
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithBidMediaTypeAsBanner() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                AppnexusBidExtAppnexus.builder().bidAdType(BANNER_TYPE).build())))
                                        .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).element(0)
                .returns(MediaType.banner, from(org.prebid.server.proto.response.Bid::getMediaType));
    }

    @Test
    public void extractBidsShouldThrowExceptionWhenBidExtNotDefined() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(null)
                                        .build()))
                                .build())));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .withMessage("bidResponse.bid.ext should be defined for appnexus");
    }

    @Test
    public void extractBidsShouldThrowExceptionWhenBidExtAppnexusNotDefined() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(mapper.createObjectNode())
                                        .build()))
                                .build())));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .withMessage("bidResponse.bid.ext.appnexus should be defined");
    }

    @Test
    public void extractBidsShouldThrowExceptionWhenBidExtAppnexusBidTypeNotDefined() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                AppnexusBidExtAppnexus.builder().build())))
                                        .build()))
                                .build())));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .withMessage("bidResponse.bid.ext.appnexus.bid_ad_type should be defined");
    }

    @Test
    public void extractBidsShouldThrowExceptionWhenBidExtAppnexusBidTypeUnrecognized() {
        // given
        adapterRequest = givenBidder(
                builder -> builder.bidderCode(BIDDER).bidId("bidId").adUnitCode("adUnitCode"),
                identity());

        exchangeCall = givenExchangeCall(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode")
                                        .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                AppnexusBidExtAppnexus.builder().bidAdType(42).build())))
                                        .build()))
                                .build())));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> adapter.extractBids(adapterRequest, exchangeCall))
                .withMessage("Unrecognized bid_ad_type in response from appnexus: 42");
    }

    @Test
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        adapterRequest = givenBidder(identity(), identity());

        exchangeCall = givenExchangeCall(identity(), br -> br.seatbid(null));

        // when and then
        assertThat(adapter.extractBids(adapterRequest, exchangeCall)).isEmpty();
        assertThat(adapter.extractBids(adapterRequest, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnMultipleBidBuildersIfMultipleAdUnitsInPreBidRequestAndBidsInResponse() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        exchangeCall = givenExchangeCall(identity(),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(asList(
                                        Bid.builder()
                                                .impid("adUnitCode1")
                                                .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                        AppnexusBidExtAppnexus.builder()
                                                                .bidAdType(BANNER_TYPE)
                                                                .build())))
                                                .build(),
                                        Bid.builder()
                                                .impid("adUnitCode2")
                                                .ext(mapper.valueToTree(AppnexusBidExt.of(
                                                        AppnexusBidExtAppnexus.builder()
                                                                .bidAdType(BANNER_TYPE)
                                                                .build())))
                                                .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(2)
                .extracting(org.prebid.server.proto.response.Bid::getCode)
                .containsOnly("adUnitCode1", "adUnitCode2");
    }

    private static AdapterRequest givenBidder(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<AppnexusParams.AppnexusParamsBuilder, AppnexusParams.AppnexusParamsBuilder>
                    paramsBuilderCustomizer) {

        return AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(adUnitBidBuilderCustomizer, paramsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBid(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<AppnexusParams.AppnexusParamsBuilder, AppnexusParams.AppnexusParamsBuilder>
                    paramsBuilderCustomizer) {

        // params
        final AppnexusParams.AppnexusParamsBuilder paramsBuilder = AppnexusParams.builder()
                .placementId(9848285)
                .invCode("30011");
        final AppnexusParams.AppnexusParamsBuilder paramsBuilderCustomized = paramsBuilderCustomizer
                .apply(paramsBuilder);
        final AppnexusParams params = paramsBuilderCustomized.build();

        // ad unit bid
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(mapper.valueToTree(params))
                .mediaTypes(singleton(MediaType.banner));
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer.apply(
                adUnitBidBuilderMinimal);

        return adUnitBidBuilderCustomized.build();
    }

    private PreBidRequestContext givenPreBidRequestContext(
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

    private static ExchangeCall<BidRequest, BidResponse> givenExchangeCall(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Function<BidResponse.BidResponseBuilder, BidResponse.BidResponseBuilder> bidResponseBuilderCustomizer) {

        final BidRequest.BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder();
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build();

        final BidResponse.BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder();
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return ExchangeCall.success(bidRequest, bidResponse, BidderDebug.builder().build());
    }
}
