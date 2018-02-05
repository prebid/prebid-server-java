package org.rtb.vexing.adapter.lifestreet;

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
import org.rtb.vexing.adapter.lifestreet.model.LifestreetParams;
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

public class LifestreetAdapterTest extends VertxTest {

    private static final String ADAPTER = "Lifestreet";
    private static final String UID_COOKIE = "lifestreet";
    private static final String ENDPOINT_URL = "http://endpoint.org/";
    private static final String USERSYNC_URL = "//usersync.org/";
    private static final String EXTERNAL_URL = "http://external.org/";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private Bidder bidder;
    private PreBidRequestContext preBidRequestContext;
    private ExchangeCall exchangeCall;
    private LifestreetAdapter adapter;

    @Before
    public void setUp() {
        bidder = givenBidderCustomizable(identity());
        preBidRequestContext = givenPreBidRequestContextCustomizable(identity(), identity());
        adapter = new LifestreetAdapter(ENDPOINT_URL, USERSYNC_URL, EXTERNAL_URL);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new LifestreetAdapter(null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new LifestreetAdapter(ENDPOINT_URL, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new LifestreetAdapter(ENDPOINT_URL, USERSYNC_URL, null));
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LifestreetAdapter("invalid_url", USERSYNC_URL, EXTERNAL_URL))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(adapter.usersyncInfo()).isEqualTo(UsersyncInfo.builder()
                .url("//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dlifestreet%26uid%3D%24%24visitor_cookie%24%24")
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
                givenAdUnitBidCustomizable(identity()),
                givenAdUnitBidCustomizable(builder -> builder.params(null))));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Lifestreet params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamSlotTagIsMissing() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("slot_tag", null);
        bidder = givenBidderCustomizable(builder -> builder.params(params));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Missing slot_tag param");
    }

    @Test
    public void makeHttpRequestsShouldFailIfAdUnitBidParamSlotTagIsInvalid() {
        // given
        final ObjectNode params = defaultNamingMapper.createObjectNode();
        params.set("slot_tag", new TextNode("invalid"));
        bidder = givenBidderCustomizable(builder -> builder.params(params));

        // when and then
        assertThatThrownBy(() -> adapter.makeHttpRequests(bidder, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid slot_tag param 'invalid'");
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
                                .build()))));

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
                        .mediaTypes(emptySet()))));

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
                        .sizes(singletonList(Format.builder().w(300).h(250).build()))
                        .params(mapper.valueToTree(LifestreetParams.of("slot.TAG"))));

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

        given(uidsCookie.uidFrom(eq(UID_COOKIE))).willReturn("buyerUid");

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
                                .tagid("slot.TAG")
                                .banner(Banner.builder()
                                        .w(300)
                                        .h(250)
                                        .topframe(1)
                                        .build())
                                .build()))
                        .site(Site.builder()
                                .domain("example.com")
                                .page("http://www.example.com")
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
                                .build()))));

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
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"))));

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
        bidder = givenBidderCustomizable(
                builder -> builder.bidderCode(ADAPTER).bidId("bidId1").adUnitCode("adUnitCode1"));

        exchangeCall = givenExchangeCallCustomizable(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("adUnitCode").build())),
                bidResponseBuilder -> bidResponseBuilder.id("bidResponseId")
                        .seatbid(singletonList(SeatBid.builder()
                                .seat("seatId")
                                .bid(singletonList(Bid.builder()
                                        .impid("adUnitCode1")
                                        .price(new BigDecimal("8.43"))
                                        .adm("adm1")
                                        .crid("crid1")
                                        .w(300)
                                        .h(250)
                                        .dealid("dealId1")
                                        .nurl("nurl1")
                                        .build()))
                                .build())));

        // when
        final List<org.rtb.vexing.model.response.Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
                .map(org.rtb.vexing.model.response.Bid.BidBuilder::build).collect(Collectors.toList());

        // then
        assertThat(bids)
                .containsExactly(org.rtb.vexing.model.response.Bid.builder()
                        .code("adUnitCode1")
                        .price(new BigDecimal("8.43"))
                        .adm("adm1")
                        .creativeId("crid1")
                        .width(300)
                        .height(250)
                        .dealId("dealId1")
                        .nurl("nurl1")
                        .bidder(ADAPTER)
                        .bidId("bidId1")
                        .build());
    }

    @Test
    public void extractBidsShouldReturnEmptyBidsIfEmptyOrNullBidResponse() {
        // given
        bidder = givenBidderCustomizable(identity());

        exchangeCall = givenExchangeCallCustomizable(identity(), br -> br.seatbid(null));

        // when and then
        assertThat(adapter.extractBids(bidder, exchangeCall)).isEmpty();
        assertThat(adapter.extractBids(bidder, ExchangeCall.empty(null))).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnOnlyFirstBidBuilderFromMultipleBidsInResponse() {
        // given
        bidder = Bidder.from(ADAPTER, asList(
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode1")),
                givenAdUnitBidCustomizable(builder -> builder.adUnitCode("adUnitCode2"))));

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
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {

        return Bidder.from(ADAPTER, singletonList(
                givenAdUnitBidCustomizable(adUnitBidBuilderCustomizer)));
    }

    private static AdUnitBid givenAdUnitBidCustomizable(
            Function<AdUnitBid.AdUnitBidBuilder, AdUnitBid.AdUnitBidBuilder> adUnitBidBuilderCustomizer) {
        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderMinimal = AdUnitBid.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .params(mapper.valueToTree(LifestreetParams.of("slot.tag1")))
                .mediaTypes(singleton(MediaType.banner));

        final AdUnitBid.AdUnitBidBuilder adUnitBidBuilderCustomized = adUnitBidBuilderCustomizer
                .apply(adUnitBidBuilderMinimal);

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
