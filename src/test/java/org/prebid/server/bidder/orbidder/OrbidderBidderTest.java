package org.prebid.server.bidder.orbidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.orbidder.ExtImpOrbidder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class OrbidderBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private OrbidderBidder orbidderBidder;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        orbidderBidder = new OrbidderBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new OrbidderBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnProperBidFloorCur() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = orbidderBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloorcur)
                .containsExactly("EUR");
    }

    @Test
    public void makeHttpRequestsShouldReturnConvertedBidFloorCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.bidfloor(BigDecimal.TEN).bidfloorcur("USD"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = orbidderBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsOnly(tuple(BigDecimal.ONE, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("false");

        // when
        final Result<List<BidderBid>> result = orbidderBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorsWhenSeatBidIsEmptyList() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = orbidderBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result).isNotNull()
                .extracting(Result::getValue, Result::getErrors)
                .containsOnly(Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void makeBidsShouldReturnErrorsWhenBidsEmptyList()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(
                        BidResponse.builder()
                                .seatbid(singletonList(SeatBid.builder().bid(emptyList()).build()))
                                .build()));

        // when
        final Result<List<BidderBid>> result = orbidderBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result).isNotNull()
                .extracting(Result::getValue, Result::getErrors)
                .containsOnly(Collections.emptyList(), Collections.emptyList());
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer,
            UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123"))
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpOrbidder.of("account_id", "placement_id", BigDecimal.ONE))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
