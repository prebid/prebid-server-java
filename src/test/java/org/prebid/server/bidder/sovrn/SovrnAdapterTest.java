package org.prebid.server.bidder.sovrn;

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
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.sovrn.proto.SovrnParams;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.MediaType;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class SovrnAdapterTest extends VertxTest {

    private static final String BIDDER = "sovrn";
    private static final String COOKIE_FAMILY = BIDDER;
    private static final String ENDPOINT_URL = "http://endpoint.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private AdapterRequest adapterRequest;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall<BidRequest, BidResponse> exchangeCall;
    private SovrnAdapter adapter;

    @Before
    public void setUp() {
        adapterRequest = givenBidder(identity());
        preBidRequestContext = givenPreBidRequestContext(identity(), identity());
        adapter = new SovrnAdapter(COOKIE_FAMILY, ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new SovrnAdapter(null, null));
        assertThatNullPointerException().isThrownBy(() -> new SovrnAdapter(COOKIE_FAMILY, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SovrnAdapter(COOKIE_FAMILY, "invalid_url"))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedHeaders() {
        // given
        given(uidsCookie.uidFrom(COOKIE_FAMILY)).willReturn("990011");
        preBidRequestContext = givenPreBidRequestContext(
                preBidRequestContextBuilder -> preBidRequestContextBuilder.ua("userAgent").ip("192.168.244.1"),
                preBidRequestBuilder -> preBidRequestBuilder
                        .device(Device.builder().dnt(11).language("fr").build()));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).flatExtracting(r -> r.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("User-Agent", "userAgent"),
                        tuple("X-Forwarded-For", "192.168.244.1"),
                        tuple("DNT", "11"),
                        tuple("Accept-Language", "fr"),
                        tuple("Cookie", "ljt_reader=990011"));
    }

    @Test
    public void makeHttpRequestsShouldFailIfParamsMissingInAtLeastOneAdUnitBid() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(givenAdUnitBid(
                adUnitBidBuilder -> adUnitBidBuilder
                        .params(null)
        )));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Sovrn params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamsCouldNotBeParsed() {
        // given
        final ObjectNode params = mapper.createObjectNode();
        params.set("bidfloor", new TextNode("non-float"));
        adapterRequest = givenBidder(adUnitBidBuilder -> adUnitBidBuilder.params(params));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Cannot deserialize value of type");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsEmpty() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(emptySet())
                )));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
    }

    @Test
    public void makeHttpRequestsShouldFailIfMediaTypeIsVideo() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                givenAdUnitBid(builder -> builder
                        .adUnitCode("adUnitCode1")
                        .mediaTypes(EnumSet.of(MediaType.video))
                )));

        preBidRequestContext = givenPreBidRequestContext(identity(), identity());

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithUserFromPreBidRequestIfAppPresent() {
        // given
        preBidRequestContext = givenPreBidRequestContext(identity(), builder -> builder
                .app(App.builder().build())
                .user(User.builder().buyeruid("110099").build()));

        given(uidsCookie.uidFrom(eq(BIDDER))).willReturn("buyerUidFromCookie");

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .extracting(r -> r.getPayload().getUser())
                .containsOnly(User.builder().buyeruid("110099").build());
    }

    @Test
    public void makeHttpRequestsShouldReturnListWithOneRequestIfMultipleAdUnitsInPreBidRequest() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        // when
        final List<AdapterHttpRequest<BidRequest>> httpRequests = adapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        assertThat(httpRequests).hasSize(1)
                .flatExtracting(r -> r.getPayload().getImp()).hasSize(2)
                .extracting(Imp::getId).containsOnly("adUnitCode1", "adUnitCode2");
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestsWithExpectedFields() {
        // given
        adapterRequest = givenBidder(
                builder -> builder
                        .bidderCode(BIDDER)
                        .adUnitCode("adUnitCode1")
                        .params(mapper.valueToTree(SovrnParams.of("tagid", null)))
                        .instl(1)
                        .topframe(1)
                        .sizes(singletonList(Format.builder().w(300).h(250).build())));

        preBidRequestContext = givenPreBidRequestContext(
                builder -> builder
                        .referer("http://www.example.com")
                        .domain("example.com")
                        .ip("192.168.144.1")
                        .ua("userAgent"),
                builder -> builder
                        .tid("tid1")
                        .timeoutMillis(1500L)
                        .user(User.builder().ext(mapper.valueToTree(ExtUser.of(null, "consent", null, null))).build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .device(Device.builder()
                                .pxratio(new BigDecimal("4.2"))
                                .build())
        );

        given(uidsCookie.uidFrom(eq(COOKIE_FAMILY))).willReturn("110099");

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
                                .tagid("tagid")
                                .banner(Banner.builder()
                                        .w(300)
                                        .h(250)
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
                                .build())
                        .device(Device.builder()
                                .ua("userAgent")
                                .ip("192.168.144.1")
                                .pxratio(new BigDecimal("4.2"))
                                .build())
                        .user(User.builder()
                                .buyeruid("110099")
                                .ext(mapper.valueToTree(ExtUser.of(null, "consent", null, null)))
                                .build())
                        .regs(Regs.of(0, mapper.valueToTree(ExtRegs.of(1))))
                        .source(Source.builder()
                                .fd(1)
                                .tid("tid1")
                                .build())
                        .build());
    }

    @Test
    public void extractBidsShouldFailIfBidImpIdDoesNotMatchAdUnitCode() {
        // given
        adapterRequest = givenBidder(builder -> builder.adUnitCode("adUnitCode"));

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
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        adapterRequest = givenBidder(identity());

        exchangeCall = givenExchangeCall(identity(), br -> br.seatbid(null));

        // when and then
        assertThat(adapter.extractBids(adapterRequest, exchangeCall)).isEmpty();
        assertThat(adapter.extractBids(adapterRequest, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnMultipleBidBuildersIfMultipleAdUnitsInPreBidRequestAndBidsInResponse() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2"))));

        exchangeCall = givenExchangeCall(identity(),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(asList(
                                        Bid.builder().impid("adUnitCode1").build(),
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

    @Test
    public void extractBidsShouldReturnBidsBuilderWithExpectedFields() {
        // given
        adapterRequest = givenBidder(
                adUnitBidBuilder -> adUnitBidBuilder
                        .bidId("bidId")
                        .bidderCode("bidderCode")
                        .adUnitCode("adUnitCode"));

        exchangeCall = givenExchangeCall(
                identity(),
                bidResponseBuilder -> bidResponseBuilder
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder()
                                        .price(BigDecimal.ONE)
                                        .impid("adUnitCode")
                                        .adm("%3Cdiv%3EThis%20is%20an%20Ad%3C%2Fdiv%3E")
                                        .crid("crid")
                                        .w(100)
                                        .h(200)
                                        .dealid("dealid")
                                        .nurl("nurl")
                                        .build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(1)
                .element(0)
                .isEqualTo(org.prebid.server.proto.response.Bid.builder()
                        .bidId("bidId")
                        .code("adUnitCode")
                        .bidder("bidderCode")
                        .price(BigDecimal.ONE)
                        .adm("<div>This is an Ad</div>")
                        .creativeId("crid")
                        .width(100)
                        .height(200)
                        .dealId("dealid")
                        .nurl("nurl")
                        .build());
    }

    @Test
    public void extractBidsShouldReturnBidBuildersOrderedByBidPrice() {
        // given
        adapterRequest = AdapterRequest.of(BIDDER, asList(
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode2")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode3")),
                givenAdUnitBid(builder -> builder.adUnitCode("adUnitCode4"))));

        exchangeCall = givenExchangeCall(identity(),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(asList(
                                        Bid.builder().impid("adUnitCode1").price(BigDecimal.TEN).build(),
                                        Bid.builder().impid("adUnitCode2").price(BigDecimal.ONE).build(),
                                        Bid.builder().impid("adUnitCode3").price(BigDecimal.valueOf(999)).build(),
                                        Bid.builder().impid("adUnitCode4").price(BigDecimal.ZERO).build()))
                                .build())));

        // when
        final List<org.prebid.server.proto.response.Bid> bids =
                adapter.extractBids(adapterRequest, exchangeCall).stream()
                        .map(org.prebid.server.proto.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids).hasSize(4)
                .extracting(
                        org.prebid.server.proto.response.Bid::getCode,
                        org.prebid.server.proto.response.Bid::getPrice)
                .containsOnly(
                        tuple("adUnitCode4", BigDecimal.ZERO),
                        tuple("adUnitCode2", BigDecimal.ONE),
                        tuple("adUnitCode1", BigDecimal.TEN),
                        tuple("adUnitCode3", BigDecimal.valueOf(999)));
    }

    private static AdapterRequest givenBidder(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        return AdapterRequest.of(BIDDER, singletonList(givenAdUnitBid(adUnitBidBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBid(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {

        // ad unit bid
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .mediaTypes(singleton(MediaType.banner))
                .params(mapper.valueToTree(SovrnParams.of("tagid", 14f)));
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
