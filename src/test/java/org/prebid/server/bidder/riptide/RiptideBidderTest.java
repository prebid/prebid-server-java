package org.prebid.server.bidder.riptide;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.riptide.ExtImpRiptide;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

@ExtendWith(MockitoExtension.class)
public class RiptideBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/ortb";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private RiptideBidder target;

    @BeforeEach
    public void setUp() {
        target = new RiptideBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RiptideBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void creationShouldFailOnEndpointWithMacros() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RiptideBidder("https://{{Host}}/ortb", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpHasNoBidderExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp-1").banner(Banner.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("imp imp-1: bidder params: missing"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(
                mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode().put("bidfloor", "x")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getMessage()).startsWith("imp imp-1: bidder params: "));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPublisherOrPlacementIdIsMissing() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("no-publisher"), ExtImpRiptide.of("", "tag", null)),
                        givenImp(imp -> imp.id("no-placement"), ExtImpRiptide.of("pub", null, null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("imp no-publisher: publisherId and placementId are required"),
                BidderError.badInput("imp no-placement: publisherId and placementId are required"));
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdAndSitePublisherAndKeepImpExtOtherThanPrebid() {
        // given
        final ObjectNode ext = givenImpExt(ExtImpRiptide.of("pub1", "tag-1", null)).put("gpid", "/1/home");
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(ext),
                request -> request.site(Site.builder().page("https://publisher.example/")
                        .publisher(Publisher.builder().name("Publisher").build()).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).singleElement().satisfies(httpRequest -> {
            assertThat(httpRequest.getUri()).isEqualTo(ENDPOINT_URL);
            assertThat(httpRequest.getImpIds()).containsExactly("imp-1");
            assertThat(httpRequest.getHeaders().get(HttpUtil.X_OPENRTB_VERSION_HEADER)).isEqualTo("2.6");
            final BidRequest outgoing = httpRequest.getPayload();
            assertThat(outgoing.getSite().getPage()).isEqualTo("https://publisher.example/");
            assertThat(outgoing.getSite().getPublisher())
                    .isEqualTo(Publisher.builder().name("Publisher").id("pub1").build());
            final Imp imp = outgoing.getImp().getFirst();
            assertThat(imp.getTagid()).isEqualTo("tag-1");
            assertThat(imp.getExt()).isEqualTo(mapper.createObjectNode().put("gpid", "/1/home"));
        });
    }

    @Test
    public void makeHttpRequestsShouldAddSiteWhenRequestHasNoInventoryObject() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsExactly(Site.builder().publisher(Publisher.builder().id("pub1").build()).build());
        assertThat(result.getValue().getFirst().getPayload().getImp().getFirst().getExt()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldSetAppAndDoohPublisherWithoutAddingSite() {
        // given
        final BidRequest appRequest = givenBidRequest(identity(),
                request -> request.app(App.builder().bundle("com.example.app").build()));
        final BidRequest doohRequest = givenBidRequest(identity(),
                request -> request.dooh(Dooh.builder().venuetype(List.of("transit")).build()));

        // when
        final BidRequest app = target.makeHttpRequests(appRequest).getValue().getFirst().getPayload();
        final BidRequest dooh = target.makeHttpRequests(doohRequest).getValue().getFirst().getPayload();

        // then
        assertThat(app.getApp().getPublisher().getId()).isEqualTo("pub1");
        assertThat(app.getApp().getBundle()).isEqualTo("com.example.app");
        assertThat(app.getSite()).isNull();
        assertThat(dooh.getDooh().getPublisher().getId()).isEqualTo("pub1");
        assertThat(dooh.getSite()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldSendOneRequestPerPublisher() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("a"), ExtImpRiptide.of("pub2", "t", null)),
                        givenImp(imp -> imp.id("b"), ExtImpRiptide.of("pub1", "t", null)),
                        givenImp(imp -> imp.id("c"), ExtImpRiptide.of("pub2", "t", null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getSite().getPublisher().getId(),
                        HttpRequest::getImpIds)
                .containsExactly(
                        tuple("pub1", Set.of("b")),
                        tuple("pub2", Set.of("a", "c")));
    }

    @Test
    public void makeHttpRequestsShouldUseParamFloorOnlyWhenImpHasNone() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("no-floor"), ExtImpRiptide.of("pub1", "t", BigDecimal.ONE)),
                        givenImp(imp -> imp.id("own-floor").bidfloor(BigDecimal.TEN).bidfloorcur("USD"),
                                ExtImpRiptide.of("pub1", "t", BigDecimal.ONE))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().getFirst().getPayload().getImp())
                .extracting(Imp::getId, Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(
                        tuple("no-floor", BigDecimal.ONE, "USD"),
                        tuple("own-floor", BigDecimal.TEN, "USD"));
        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldConvertImpFloorInAnotherCurrencyToUsd() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR"));
        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "EUR", "USD"))
                .willReturn(BigDecimal.valueOf(11));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getPayload().getImp())
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.valueOf(11), "USD"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenFloorCannotBeConverted() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("XYZ"));
        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "XYZ", "USD"))
                .willThrow(new PreBidException("no rate"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("imp imp-1: floor XYZ: no rate"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall("invalid"), givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("bad server response: ");
        });
    }

    @Test
    public void makeBidsShouldReturnNoBidsForCodedNoBidOrEmptySeatbid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> coded = target.makeBids(givenHttpCall("{\"id\":\"r\",\"nbr\":2}"), bidRequest);
        final Result<List<BidderBid>> empty = target.makeBids(
                givenHttpCall(mapper.writeValueAsString(BidResponse.builder().id("r").build())), bidRequest);

        // then
        assertThat(coded.getValue()).isEmpty();
        assertThat(coded.getErrors()).isEmpty();
        assertThat(empty.getValue()).isEmpty();
        assertThat(empty.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldTypeBidsByMtype() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(Banner.builder().build())
                .video(Video.builder().build()).audio(Audio.builder().build()).xNative(Native.builder().build()));
        final String response = givenBidResponse(
                givenBid(bid -> bid.id("1").mtype(1)),
                givenBid(bid -> bid.id("2").mtype(2).dur(15).cat(List.of("IAB1", "IAB2"))),
                givenBid(bid -> bid.id("3").mtype(3)),
                givenBid(bid -> bid.id("4").mtype(4)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(banner, video, audio, xNative);
        assertThat(result.getValue()).extracting(BidderBid::getBidCurrency).containsOnly("USD");
        assertThat(result.getValue().get(1).getVideoInfo().getDuration()).isEqualTo(15);
        assertThat(result.getValue().get(1).getVideoInfo().getPrimaryCategory()).isEqualTo("IAB1");
        assertThat(result.getValue().getFirst().getVideoInfo()).isNull();
    }

    @Test
    public void makeBidsShouldTypeBidWithoutMtypeByItsSingleFormatImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).video(Video.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(
                givenHttpCall(givenBidResponse(givenBid(identity()))), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(video);
        assertThat(result.getValue().getFirst().getVideoInfo().getDuration()).isZero();
        assertThat(result.getValue().getFirst().getVideoInfo().getPrimaryCategory()).isNull();
    }

    @Test
    public void makeBidsShouldReturnErrorsForUntypedUnknownAndUnmatchedBids() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.video(Video.builder().build()));
        final String response = givenBidResponse(
                givenBid(bid -> bid.id("untyped")),
                givenBid(bid -> bid.id("unknown").mtype(9)),
                givenBid(bid -> bid.id("orphan").impid("nope").mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("bid untyped has no mtype and imp imp-1 is not single-format"),
                BidderError.badServerResponse("bid unknown has unknown mtype 9"),
                BidderError.badServerResponse("bid orphan answers unknown imp nope"));
    }

    @Test
    public void makeBidsShouldUseResponseCurrencyWhenPresent() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().cur("EUR")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(givenBid(bid -> bid.mtype(1)))).build()))
                .build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(givenHttpCall(response), givenBidRequest(identity()));

        // then
        assertThat(result.getValue()).extracting(BidderBid::getBidCurrency).containsExactly("EUR");
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {

        return requestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, ExtImpRiptide.of("pub1", "tag-1", null)))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer, ExtImpRiptide params) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp-1")
                        .banner(Banner.builder().w(300).h(250).build())
                        .ext(givenImpExt(params)))
                .build();
    }

    private static ObjectNode givenImpExt(ExtImpRiptide params) {
        return mapper.valueToTree(ExtPrebid.of(null, params));
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder().id("bid-1").impid("imp-1").price(BigDecimal.ONE).adm("<div/>"))
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .id("r")
                .seatbid(singletonList(SeatBid.builder().seat("riptide").bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body), null);
    }
}
