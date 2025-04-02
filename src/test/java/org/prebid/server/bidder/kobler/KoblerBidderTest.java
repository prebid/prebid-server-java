package org.prebid.server.bidder.kobler;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kobler.ExtImpKobler;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KoblerBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com";
    private static final String DEV_ENDPOINT = "https://dev.test.com";
    @Mock
    private CurrencyConversionService currencyConversionService;

    private KoblerBidder target;

    @BeforeEach
    public void setUp() {
        target = new KoblerBidder(
                ENDPOINT_URL,
                DEV_ENDPOINT,
                currencyConversionService,
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new KoblerBidder(
                        "invalid_url",
                        DEV_ENDPOINT,
                        currencyConversionService,
                        jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                imp -> imp.bidfloor(BigDecimal.ONE).bidfloorcur("EUR")));

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("Currency conversion failed"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Currency conversion failed");
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                imp -> imp.bidfloor(BigDecimal.ONE).bidfloorcur("EUR")));

        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenReturn(BigDecimal.TEN);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldUseDevEndpointWhenTestModeEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(true))))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(DEV_ENDPOINT);
    }

    @Test
    public void makeHttpRequestsShouldUseDefaultEndpointWhenTestModeDisabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldAddUsdToCurrenciesIfMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.cur(singletonList("EUR")),
                givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getCur)
                .containsExactly(List.of("EUR", "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyIsInvalid() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenSeatbidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithBannerType() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .ext(mapper.valueToTree(Map.of("prebid", Map.of("type", "banner"))))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnBannerWhenTypeIsNullOrMissing() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .ext(mapper.valueToTree(Map.of("prebid", Map.of("type", "null"))))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldDefaultToBannerWhenPrebidTypeIsMissing() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .ext(mapper.valueToTree(Map.of("prebid", Map.of())))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .cur(singletonList("USD"))
                        .imp(List.of(imps)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(false)))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCallWithBody(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return givenHttpCallWithBody(mapper.writeValueAsString(bidResponse));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build();
    }
}
