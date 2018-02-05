package org.rtb.vexing.adapter.facebook;

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
import org.rtb.vexing.adapter.facebook.model.FacebookExt;
import org.rtb.vexing.adapter.facebook.model.FacebookParams;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.Video;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class FacebookAdapterTest extends VertxTest {

    private static final String ADAPTER = "audienceNetwork";
    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String NONSECURE_ENDPOINT_URL = ENDPOINT_URL;
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String PLATFORM_ID = "100";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall exchangeCall;
    private FacebookAdapter adapter;

    @Before
    public void setUp() {
        bidder = givenBidderCustomizable(identity(), identity());
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());
        adapter = new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, PLATFORM_ID);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter("invalid_url", NONSECURE_ENDPOINT_URL, USERSYNC_URL, PLATFORM_ID))
                .withMessage("URL supplied is not valid: invalid_url");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter(ENDPOINT_URL, "invalid_url", USERSYNC_URL, PLATFORM_ID))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailOnInvalidPlatformId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacebookAdapter(ENDPOINT_URL, NONSECURE_ENDPOINT_URL, USERSYNC_URL, "non-number"))
                .withMessage("Platform ID is not valid number: 'non-number'");
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
                        tuple("Accept", "application/json"));
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
                .hasMessage("Facebook params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidHasInvalidFieldsForBanner() {
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(new HashSet<>(singletonList(MediaType.banner)))
                        .instl(0)
                        .sizes(singletonList(Format.builder().h(42).build())),
                identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Facebook do not support banner height other than 50, 90 and 250");
    }

    @Test
    public void requestBidsShouldFailIfPlacementIdParamIsMissing() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("placementId", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing placementId param");
    }

    @Test
    public void requestBidsShouldFailIfPlacementIdParamHasInvalidFormat() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("placementId", new TextNode("invalid-placement-id"));
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid placementId param 'invalid-placement-id'");
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
    public void makeHttpRequestsShouldFailIfMediaTypeIsEmpty() {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .adUnitCode("adUnitCode1")
                                .mediaTypes(emptySet()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
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
    public void makeHttpRequestsShouldReturnRequestWithRandomEndpoint() {
        // given
        adapter = new FacebookAdapter("https://secure-endpoint.org", "http://non-secure-endpoint.org", USERSYNC_URL,
                PLATFORM_ID);
        preBidRequestContext = givenPreBidRequestContextCustomizable(builder -> builder.isDebug(true), identity());

        // when
        final List<List<HttpRequest>> listOfHttpRequests = IntStream.range(0, 36)
                .mapToObj(value -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .collect(Collectors.toList());

        // then
        final boolean usedSecureUrl = listOfHttpRequests.stream().flatMap(List::stream)
                .anyMatch(httpRequests -> httpRequests.uri.equals("https://secure-endpoint.org"));
        assertThat(usedSecureUrl).isTrue();

        final boolean usedNonSecureUrl = listOfHttpRequests.stream().flatMap(List::stream)
                .anyMatch(httpRequests -> httpRequests.uri.equals("http://non-secure-endpoint.org"));
        assertThat(usedNonSecureUrl).isTrue();
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
                                        .w(300)
                                        .h(250)
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
                        .width(300)
                        .height(250)
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
                                .bid(asList(Bid.builder().impid("adUnitCode1").build(),
                                        Bid.builder().impid("adUnitCode2").build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(bid -> bid.code)
                .containsOnly("adUnitCode1");
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
