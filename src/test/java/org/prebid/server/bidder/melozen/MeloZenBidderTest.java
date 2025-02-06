package org.prebid.server.bidder.melozen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.melozen.MeloZenImpExt;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
class MeloZenBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/{{PublisherID}}";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private MeloZenBidder target;

    @BeforeEach
    public void before() {
        target = new MeloZenBidder(currencyConversionService, ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() ->
                new MeloZenBidder(currencyConversionService, "invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value");
        });
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp1"), imp -> imp.id("givenImp2"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactlyInAnyOrderElementsOf(bidRequest.getImp());
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImpPerMediaType() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .xNative(Native.builder().build()),
                imp -> imp.id("givenImp2")
                        .banner(null)
                        .xNative(null)
                        .video(Video.builder().build()));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(4)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactlyInAnyOrder(
                        givenImp(imp -> imp.id("givenImp1").banner(Banner.builder().build()).video(null).xNative(null)),
                        givenImp(imp -> imp.id("givenImp1").banner(null).video(Video.builder().build()).xNative(null)),
                        givenImp(imp -> imp.id("givenImp1").banner(null).video(null).xNative(Native.builder().build())),
                        givenImp(imp -> imp.id("givenImp2").banner(null).video(Video.builder().build()).xNative(null)));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpDoesNotHaveBannerVideoOrNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impid").banner(null).video(null).xNative(null));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsOnly(
                BidderError.badInput("Invalid MediaType. MeloZen only supports Banner, Video and Native."));
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp1"), imp -> imp.id("givenImp2"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(singleton("givenImp1"), singleton("givenImp2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("impId1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.id("impId2").banner(null).video(null).xNative(null),
                imp -> imp.id("impId3"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("impId3");
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com/publisherId");
    }

    @Test
    public void makeHttpRequestsShouldNotConvertBidfloorWhenBidfloorHasUSDCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("USD"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, "USD"));

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldNotConvertBidfloorAndAssignUSDCurrencyWhenBidfloorIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(null).bidfloorcur("EUR"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(null, "EUR"));

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldNotConvertBidfloorWhenBidfloorHasEmptyCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.TEN, null));

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldConvertBidfloorToUSDWhenBidfloorHasAnotherCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("EUR"));

        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "EUR", "USD"))
                .willReturn(BigDecimal.ONE);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.ONE, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseDoesNotHaveSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse());

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidBasedOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "banner");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid bannerBid = Bid.builder().impid("1").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, null));

    }

    @Test
    public void makeBidsShouldReturnVideoBidBasedOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "video");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid videoBid = Bid.builder().impid("2").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, video, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "native");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid nativeBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, null));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtCanNotResolveType() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "unknown");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid bid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsOnly(BidderError.badServerResponse("Failed to parse bid mediatype for impression \"3\""));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(MeloZenBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, MeloZenImpExt.of("publisherId")))))
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .build());
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
