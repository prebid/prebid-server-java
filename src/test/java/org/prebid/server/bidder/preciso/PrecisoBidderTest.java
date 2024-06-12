package org.prebid.server.bidder.preciso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.preciso.ExtImpPreciso;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class PrecisoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    private PrecisoBidder target;

    @Before
    public void setUp() {
        target = new PrecisoBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PrecisoBidder(
                "invalid_url", currencyConversionService, jacksonMapper));

    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();

    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {

        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();

    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(bid -> bid.ext(givenBidExt(BidType.banner)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfRequestCurrencyDoesNotMatchBidderCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                 .willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = BidRequest.builder()
                        .imp(singletonList(Imp.builder().bidfloor(BigDecimal.ONE).bidfloorcur("EUR")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode()))).build()))
                        .id("request_id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getPayload)
                        .flatExtracting(BidRequest::getImp)
                        .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                        .containsOnly(tuple(BigDecimal.TEN, "USD"));

    }

    @Test
    public void makeHttpRequestsShouldTakePriceFloorsWhenBidfloorParamIsAlsoPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().bidfloor(BigDecimal.TEN).bidfloorcur("USD")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpPreciso.builder()
                        .bidFloor(BigDecimal.ONE).build())))
                        .build()))
                .id("request_id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getPayload)
        .flatExtracting(BidRequest::getImp)
        .extracting(Imp::getBidfloor, Imp::getBidfloorcur).containsOnly(tuple(BigDecimal.TEN, "USD"));

    }

    @Test
    public void makeHttpRequestsShouldTakeBidfloorExtImpParamIfNoBidfloorInRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpPreciso.builder()
                                .bidFloor(BigDecimal.valueOf(16)).build())))
                .build()))
                .id("request_id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getPayload)
               .flatExtracting(BidRequest::getImp)
               .extracting(Imp::getBidfloor).containsExactly(BigDecimal.valueOf(16));

    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageOnFailedCurrencyConversion() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                  .willThrow(PreBidException.class);

        final BidRequest bidRequest = BidRequest.builder()
                  .imp(singletonList(Imp.builder().id("123").bidfloor(BigDecimal.ONE).bidfloorcur("EUR")
                  .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode()))).build()))
                  .id("request_id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage())
                    .isEqualTo("Unable to convert provided bid floor currency from EUR to USD for imp `123`");
        });
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                   HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                   HttpResponse.of(200, null, body),
                   null);
    }

    private static ObjectNode givenBidExt(BidType bidType) {
        final ObjectNode ext = mapper.createObjectNode();
        ext.putObject("prebid").put("type", bidType.getName());
        return ext;
    }
}
