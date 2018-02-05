package org.rtb.vexing.adapter.conversant;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
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
import org.rtb.vexing.adapter.conversant.model.ConversantParams;
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

public class ConversantAdapterTest extends VertxTest {

    private static final String ADAPTER = "conversant";
    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String EXTERNAL_URL = "http://external.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall exchangeCall;
    private ConversantAdapter adapter;

    @Before
    public void setUp() {
        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUid1");

        bidder = givenBidderCustomizable(identity(), identity());
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());
        adapter = new ConversantAdapter(ENDPOINT_URL, USERSYNC_URL, EXTERNAL_URL);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new ConversantAdapter(null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new ConversantAdapter(ENDPOINT_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new ConversantAdapter(ENDPOINT_URL, USERSYNC_URL, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConversantAdapter("invalid_url", USERSYNC_URL, EXTERNAL_URL))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(adapter.usersyncInfo()).isEqualTo(UsersyncInfo.builder()
                .url("//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dconversant%26uid%3D")
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
    public void makeHttpRequestsShouldReturnEmptyRequestsIfNoAppAndNoCookie() {
        // given
        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn(null);
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder.app(null));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).isEmpty();
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
                .hasMessage("Conversant params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("secure", new TextNode("non-integer"));
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamPublisherIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("site_id", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing site id");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithValidVideoApiFromAdUnitParam() {
        // given
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.api(asList(1, 3, 6, 100)));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getApi())
                .containsOnly(1, 3, 6);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithValidVideoProtocolsFromAdUnitParam() {
        // given
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.protocols(asList(1, 5, 10, 100)));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getProtocols())
                .containsOnly(1, 5, 10);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithVideoProtocolsFromAdUnitFieldIfParamIsMissing() {
        // given
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(200)).build()),
                builder -> builder.protocols(null));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getProtocols())
                .containsOnly(200);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithVideoAdPositionFromAdUnitParam() {
        // given
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.position(0));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).isNotNull()
                .extracting(imp -> imp.getVideo().getPos())
                .containsOnly(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithNullVideoAdPositionIfInvalidAdUnitParam() {
        // given
        bidder = givenBidderCustomizable(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.position(100));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.bidRequest.getImp()).isNotNull()
                .extracting(imp -> imp.getVideo().getPos())
                .hasSize(1)
                .containsNull();
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
    public void makeHttpRequestsShouldReturnRequestsWithExpectedFields() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder
                        .bidderCode(ADAPTER)
                        .adUnitCode("adUnitCode1")
                        .sizes(singletonList(Format.builder().w(300).h(250).build())),
                paramsBuilder -> paramsBuilder
                        .siteId("siteId42")
                        .secure(12)
                        .tagId("tagId42")
                        .position(3)
                        .bidfloor(1.03F)
                        .mobile(87)
                        .mimes(singletonList("mime42"))
                        .api(singletonList(10))
                        .protocols(singletonList(50))
                        .maxduration(40));

        preBidRequestContext = givenPreBidRequestContextCustomizable(
                builder -> builder
                        .timeout(1500L)
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent1"),
                builder -> builder
                        .tid("tid1")
        );

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.bidRequest)
                .containsOnly(BidRequest.builder()
                        .id("tid1")
                        .at(1)
                        .tmax(1500L)
                        .imp(singletonList(Imp.builder()
                                .id("adUnitCode1")
                                .tagid("tagId42")
                                .banner(Banner.builder()
                                        .w(300)
                                        .h(250)
                                        .format(singletonList(Format.builder()
                                                .w(300)
                                                .h(250)
                                                .build()))
                                        .pos(3)
                                        .build())
                                .displaymanager("prebid-s2s")
                                .bidfloor(1.03F)
                                .secure(12)
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
                                .id("siteId42")
                                .mobile(87)
                                .build())
                        .device(Device.builder()
                                .ua("userAgent1")
                                .ip("192.168.144.1")
                                .build())
                        .user(User.builder()
                                .buyeruid("buyerUid1")
                                .build())
                        .source(Source.builder()
                                .fd(1)
                                .tid("tid1")
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithAppFromPreBidRequest() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().id("appId").build()));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.bidRequest.getApp().getId())
                .containsOnly("appId");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("buyerUid").build()));

        given(uidsCookie.uidFrom(eq(ADAPTER))).willReturn("buyerUidFromCookie");

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.bidRequest.getUser())
                .containsOnly(User.builder().buyeruid("buyerUid").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithOneRequestIfMultipleAdUnitsInPreBidRequest() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1"), identity()),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"), identity())));

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void extractBidsShouldFailIfBidImpIdDoesNotMatchAdUnitCode() {
        // given
        bidder = givenBidderCustomizable(builder -> builder.adUnitCode("adUnitCode"), identity());

        exchangeCall = givenExchangeCallCustomizable(identity(),
                bidResponseBuilder -> bidResponseBuilder.seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("anotherAdUnitCode").build()))
                        .build())));

        // when and then
        assertThatThrownBy(() -> adapter.extractBids(bidder, exchangeCall))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Unknown ad unit code 'anotherAdUnitCode'");
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithOneRequestIfAdUnitContainsBannerAndVideoMediaTypes() {
        //given
        bidder = Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime1"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());

        // when
        final List<HttpRequest> httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.bidRequest.getImp()).hasSize(2)
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
    }

    @Test
    public void extractBidsShouldReturnBidBuildersWithExpectedFields() {
        // given
        bidder = givenBidderCustomizable(
                builder -> builder.bidderCode(ADAPTER).bidId("bidId").adUnitCode("adUnitCode"),
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
                        .bidder(ADAPTER)
                        .bidId("bidId")
                        .mediaType(MediaType.banner)
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
    public void extractBidsShouldReturnMultipleBidBuildersIfMultipleAdUnitsInPreBidRequestAndBidsInResponse() {
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
        assertThat(bids).hasSize(2)
                .extracting(bid -> bid.code)
                .containsOnly("adUnitCode1", "adUnitCode2");
    }

    private static Bidder givenBidderCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<ConversantParams.ConversantParamsBuilder, ConversantParams.ConversantParamsBuilder>
                    paramsBuilderCustomizer) {

        return Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer, paramsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<ConversantParams.ConversantParamsBuilder, ConversantParams.ConversantParamsBuilder>
                    paramsBuilderCustomizer) {

        // params
        final ConversantParams.ConversantParamsBuilder paramsBuilder = ConversantParams.builder()
                .siteId("siteId1")
                .secure(42)
                .tagId("tagId1")
                .position(2)
                .bidfloor(7.32F)
                .mobile(64)
                .mimes(singletonList("mime1"))
                .api(singletonList(1))
                .protocols(singletonList(5))
                .maxduration(30);
        final ConversantParams.ConversantParamsBuilder paramsBuilderCustomized = paramsBuilderCustomizer
                .apply(paramsBuilder);
        final ConversantParams params = paramsBuilderCustomized.build();

        // ad unit bid
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(mapper.valueToTree(params))
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
