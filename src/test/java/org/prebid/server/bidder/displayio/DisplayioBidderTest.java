package org.prebid.server.bidder.displayio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.displayio.DisplayioImpExt;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
class DisplayioBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/{{PublisherID}}";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private DisplayioBidder target;

    @BeforeEach
    public void before() {
        target = new DisplayioBidder(currencyConversionService, ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DisplayioBidder(currencyConversionService, "invalid_url", jacksonMapper));
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
    public void makeHttpRequestsShouldReturnErrorWhenBidFloorIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.bidfloor(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("BidFloor should be defined"));
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
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(X_OPENRTB_VERSION_HEADER))
                        .isEqualTo("2.5"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("impId1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.id("impId2").bidfloor(null),
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
    public void makeHttpRequestsShouldNotConvertBidfloorAndAssignUSDCurrencyWhenBidfloorHasEmptyCurrency() {
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
                .containsOnly(tuple(BigDecimal.TEN, "USD"));

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
    public void makeHttpRequestsShouldEnrichExtRequest() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperties(Map.of(
                "property1", TextNode.valueOf("value1"),
                "property2", TextNode.valueOf("value2")));

        final BidRequest givenBidRequest = givenBidRequest(identity()).toBuilder()
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(givenBidRequest);

        // then
        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperties(Map.of(
                "property1", TextNode.valueOf("value1"),
                "property2", TextNode.valueOf("value2"),
                "displayio", mapper.createObjectNode()
                        .put("inventoryId", "inventoryId")
                        .put("placementId", "placementId")));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt).first()
                .isEqualTo(expectedExtRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
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
    public void makeBidsShouldReturnErrorWhenBidResponseHasEmptySeatbids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse("Invalid SeatBids count"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidResponseHasMoreThanOneSeatbid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .seatbid(List.of(SeatBid.builder().build(), SeatBid.builder().build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse("Invalid SeatBids count"));
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(bid -> bid.mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(bid -> bid.mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnErrorOnInvalidBidType()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();

        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("unsupported MType null"));
    }

    @Test
    public void makeBidsShouldProceedWithErrorsAndValues() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                givenBid(bid -> bid.mtype(3)),
                givenBid(bid -> bid.mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unsupported MType 3"));

        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(DisplayioBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null,
                                DisplayioImpExt.of("publisherId", "inventoryId", "placementId")))))
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
