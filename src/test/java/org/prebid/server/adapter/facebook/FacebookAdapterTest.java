package org.prebid.server.adapter.facebook;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.BidResponse.BidResponseBuilder;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.adapter.facebook.model.FacebookExt;
import org.prebid.server.adapter.facebook.model.FacebookParams;
import org.prebid.server.adapter.model.ExchangeCall;
import org.prebid.server.adapter.model.HttpRequest;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.AdUnitBid;
import org.prebid.server.model.AdUnitBid.AdUnitBidBuilder;
import org.prebid.server.model.Bidder;
import org.prebid.server.model.MediaType;
import org.prebid.server.model.PreBidRequestContext;
import org.prebid.server.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.prebid.server.model.request.PreBidRequest;
import org.prebid.server.model.request.PreBidRequest.PreBidRequestBuilder;
import org.prebid.server.model.request.Video;
import org.prebid.server.model.response.BidderDebug;
import org.prebid.server.model.response.UsersyncInfo;

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
        bidder = givenBidder(identity());
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());
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
        Assertions.assertThat(adapter.usersyncInfo()).isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedHeaders() {
        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        bidder = Bidder.of(ADAPTER, asList(
                givenAdUnitBid(identity()),
                givenAdUnitBid(builder -> builder.params(null))));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Facebook params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidHasInvalidFieldsForBanner() {
        bidder = givenBidder(builder -> builder
                .mediaTypes(new HashSet<>(singletonList(MediaType.banner)))
                .instl(0)
                .sizes(singletonList(Format.builder().h(42).build()))
        );

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Facebook do not support banner height other than 50, 90 and 250");
    }

    @Test
    public void requestBidsShouldFailIfPlacementIdParamIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("placementId", null);
        bidder = givenBidder(builder -> builder.params(params));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing placementId param");
    }

    @Test
    public void requestBidsShouldFailIfPlacementIdParamHasInvalidFormat() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("placementId", new TextNode("invalid-placement-id"));
        bidder = givenBidder(builder -> builder.params(params));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid placementId param 'invalid-placement-id'");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsVideoAndMimesListIsEmpty() {
        //given
        bidder = Bidder.of(ADAPTER, singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder()
                                .mimes(emptyList())
                                .build())
                )));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsEmpty() {
        //given
        bidder = Bidder.of(ADAPTER, singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(emptySet())
                )));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithExpectedFields() {
        // given
        bidder = givenBidder(
                builder -> builder
                        .bidderCode(ADAPTER)
                        .adUnitCode("adUnitCode")
                        .instl(1)
                        .topframe(1)
                        .params(mapper.valueToTree(FacebookParams.of("pub1_place1")))
                        .sizes(singletonList(Format.builder().w(300).h(250).build()))
        );

        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .timeoutMillis(1500L)
                        .tid("tid")
        );

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUid");

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(HttpRequest::getBidRequest)
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
                        .ext(mapper.valueToTree(FacebookExt.of(Integer.valueOf(PLATFORM_ID))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithRandomEndpoint() {
        // given
        adapter = new FacebookAdapter("https://secure-endpoint.org", "http://non-secure-endpoint.org", USERSYNC_URL,
                PLATFORM_ID);
        preBidRequestContext = givenPreBidRequestContext(builder -> builder.isDebug(true), identity());

        // when
        final List<List<HttpRequest>> listOfHttpRequests = IntStream.range(0, 36)
                .mapToObj(value -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .collect(Collectors.toList());

        // then
        final boolean usedSecureUrl = listOfHttpRequests.stream().flatMap(List::stream)
                .anyMatch(httpRequests -> httpRequests.getUri().equals("https://secure-endpoint.org"));
        assertThat(usedSecureUrl).isTrue();

        final boolean usedNonSecureUrl = listOfHttpRequests.stream().flatMap(List::stream)
                .anyMatch(httpRequests -> httpRequests.getUri().equals("http://non-secure-endpoint.org"));
        assertThat(usedNonSecureUrl).isTrue();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithAppFromPreBidRequest() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().id("appId").build()));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getBidRequest().getApp().getId())
                .containsOnly("appId");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUidFromCookie");

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .extracting(r -> r.getBidRequest().getUser())
                .containsOnly(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoRequestsIfAdUnitContainsBannerAndVideoMediaTypes() {
        //given
        bidder = Bidder.of(ADAPTER, singletonList(
                givenAdUnitBid(builder -> builder
                        .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                        .video(Video.builder()
                                .mimes(singletonList("Mime"))
                                .playbackMethod(1)
                                .build()))));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.getBidRequest().getImp())
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithMultipleRequestsIfMultipleAdUnitsInPreBidRequest() {
        // given
        bidder = Bidder.of(ADAPTER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(2)
                .flatExtracting(r -> r.getBidRequest().getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithExpectedFields() {
        // given
        bidder = givenBidder(builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"));

        exchangeCall = givenExchangeCall(
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
        final List<org.prebid.server.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.prebid.server.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids)
                .containsExactly(org.prebid.server.model.response.Bid.builder()
                        .code("adUnitCode")
                        .price(new BigDecimal("8.43"))
                        .adm("adm")
                        .width(300)
                        .height(250)
                        .bidder(ADAPTER)
                        .bidId("bidId")
                        .mediaType(MediaType.banner)
                        .build());
    }

    @Test
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        bidder = givenBidder(identity());

        exchangeCall = givenExchangeCall(identity(), br -> br.seatbid(null));

        // when and then
        Assertions.assertThat(adapter.extractBids(bidder, exchangeCall)).isEmpty();
        Assertions.assertThat(adapter.extractBids(bidder, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnOnlyFirstBidBuilderFromMultipleBidsInResponse() {
        // given
        bidder = Bidder.of(ADAPTER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        exchangeCall = givenExchangeCall(identity(),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(asList(Bid.builder().impid("adUnitCode1").build(),
                                        Bid.builder().impid("adUnitCode2").build()))
                                .build())));

        // when
        final List<org.prebid.server.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.prebid.server.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .extracting(org.prebid.server.model.response.Bid::getCode)
                .containsOnly("adUnitCode1");
    }

    private static Bidder givenBidder(Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        return Bidder.of(ADAPTER, singletonList(givenAdUnitBid(adUnitBidBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBid(Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        // ad unit bid
        final AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(mapper.valueToTree(FacebookParams.of("pubId1_placement1")))
                .mediaTypes(singleton(MediaType.banner));
        final AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer.apply(
                adUnitBidBuilderMinimal);

        return adUnitBidBuilderCustomized.build();
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
                        .uidsCookie(uidsCookie);
        return preBidRequestContextBuilderCustomizer.apply(preBidRequestContextBuilderMinimal).build();
    }

    private static ExchangeCall givenExchangeCall(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer,
            Function<BidResponseBuilder, BidResponseBuilder> bidResponseBuilderCustomizer) {

        final BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder();
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build();

        final BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder();
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return ExchangeCall.success(bidRequest, bidResponse, BidderDebug.builder().build());
    }
}
