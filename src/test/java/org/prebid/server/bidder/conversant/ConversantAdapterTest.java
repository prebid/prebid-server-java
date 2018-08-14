package org.prebid.server.bidder.conversant;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.BidResponse.BidResponseBuilder;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.auction.model.PreBidRequestContext.PreBidRequestContextBuilder;
import org.prebid.server.bidder.conversant.proto.ConversantParams;
import org.prebid.server.bidder.conversant.proto.ConversantParams.ConversantParamsBuilder;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.PreBidRequest.PreBidRequestBuilder;
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
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class ConversantAdapterTest extends VertxTest {

    private static final String BIDDER = "conversant";
    private static final String ENDPOINT_URL = "http://exchange.org/";
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String EXTERNAL_URL = "http://external.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall<BidRequest, BidResponse> exchangeCall;
    private ConversantAdapter adapter;
    private ConversantUsersyncer usersyncer;

    @Before
    public void setUp() {
        given(uidsCookie.uidFrom(eq(BIDDER))).willReturn("buyerUid1");

        adapterRequest = givenBidder(identity(), identity());
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());
        usersyncer = new ConversantUsersyncer(USERSYNC_URL, EXTERNAL_URL);
        adapter = new ConversantAdapter(usersyncer, ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new ConversantAdapter(null, null));
        assertThatNullPointerException().isThrownBy(() -> new ConversantAdapter(usersyncer, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConversantAdapter(usersyncer, "invalid_url"))
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
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(identity(), identity()),
                givenAdUnitBid(builder -> builder.params(null), identity())));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Conversant params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("secure", new TextNode("non-integer"));
        adapterRequest = givenBidder(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamPublisherIdIsMissing() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("site_id", null);
        adapterRequest = givenBidder(builder -> builder.params(params), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing site id");
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithValidVideoApiFromAdUnitParam() {
        // given
        adapterRequest = givenBidder(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.api(asList(1, 3, 6, 100)));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getApi())
                .containsOnly(1, 3, 6);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithValidVideoProtocolsFromAdUnitParam() {
        // given
        adapterRequest = givenBidder(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.protocols(asList(1, 5, 10, 100)));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getProtocols())
                .containsOnly(1, 5, 10);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithVideoProtocolsFromAdUnitFieldIfParamIsMissing() {
        // given
        adapterRequest = givenBidder(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(200)).build()),
                builder -> builder.protocols(null));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).isNotNull()
                .flatExtracting(imp -> imp.getVideo().getProtocols())
                .containsOnly(200);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithVideoAdPositionFromAdUnitParam() {
        // given
        adapterRequest = givenBidder(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.position(0));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).isNotNull()
                .extracting(imp -> imp.getVideo().getPos())
                .containsOnly(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestsWithNullVideoAdPositionIfInvalidAdUnitParam() {
        // given
        adapterRequest = givenBidder(builder -> builder
                        .mediaTypes(singleton(MediaType.video))
                        .video(Video.builder().mimes(singletonList("mime1")).build()),
                builder -> builder.position(100));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests)
                .flatExtracting(r -> r.getPayload().getImp()).isNotNull()
                .extracting(imp -> imp.getVideo().getPos())
                .hasSize(1)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsEmpty() {
        //given
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
        //given
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

        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent1"),
                builder -> builder
                        .timeoutMillis(1500L)
                        .tid("tid1")
                        .user(User.builder().ext(mapper.valueToTree(ExtUser.of(null, "consent", null))).build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
        );

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
                                .displaymanagerver("1.0.1")
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
                                .ext(mapper.valueToTree(ExtUser.of(null, "consent", null)))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .source(Source.builder()
                                .fd(1)
                                .tid("tid1")
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldThrowPrebidExceptionIfAppIsPresentOnRequest() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().build()));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Conversant doesn't support App requests");
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
    public void makeHttpRequestsShouldReturnListWithOneRequestIfAdUnitContainsBannerAndVideoMediaTypes() {
        //given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
                                .mediaTypes(EnumSet.of(MediaType.video, MediaType.banner))
                                .video(Video.builder()
                                        .mimes(singletonList("Mime1"))
                                        .playbackMethod(1)
                                        .build()),
                        identity())));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(2)
                .extracting(imp -> imp.getVideo() == null, imp -> imp.getBanner() == null)
                .containsOnly(tuple(true, false), tuple(false, true));
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
                        .bidder(BIDDER)
                        .bidId("bidId")
                        .mediaType(MediaType.banner)
                        .build());
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
                                .bid(asList(Bid.builder().impid("adUnitCode1").build(),
                                        Bid.builder().impid("adUnitCode2").build()))
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
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<ConversantParamsBuilder, ConversantParamsBuilder> paramsBuilderCustomizer) {

        return AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(adUnitBidBuilderCustomizer, paramsBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBid(
            Function<AdUnitBidBuilder, AdUnitBidBuilder> adUnitBidBuilderCustomizer,
            Function<ConversantParamsBuilder, ConversantParamsBuilder> paramsBuilderCustomizer) {

        // params
        final ConversantParamsBuilder paramsBuilder = ConversantParams.builder()
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
        final ConversantParamsBuilder paramsBuilderCustomized = paramsBuilderCustomizer
                .apply(paramsBuilder);
        final ConversantParams params = paramsBuilderCustomized.build();

        // ad unit bid
        final AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(mapper.valueToTree(params))
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

    private static ExchangeCall<BidRequest, BidResponse> givenExchangeCall(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer,
            Function<BidResponseBuilder, BidResponseBuilder> bidResponseBuilderCustomizer) {

        final BidRequestBuilder bidRequestBuilderMinimal = BidRequest.builder();
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(bidRequestBuilderMinimal).build();

        final BidResponseBuilder bidResponseBuilderMinimal = BidResponse.builder();
        final BidResponse bidResponse = bidResponseBuilderCustomizer.apply(bidResponseBuilderMinimal).build();

        return ExchangeCall.success(bidRequest, bidResponse, BidderDebug.builder().build());
    }
}
