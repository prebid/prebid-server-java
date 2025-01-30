package org.prebid.server.bidder.kobler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
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
    private static final String DEV_ENDPOINT = "https://bid-service.dev.essrtb.com/bid/prebid_server_rtb_call";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private KoblerBidder target;

    @BeforeEach
    public void setUp() {
        target = new KoblerBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KoblerBidder(
                "invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImps() {
        // Given
        BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("EUR")
                        .ext(mapper.createObjectNode())
                        .build()))
                .build();

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("Currency conversion failed"));

        // When
        Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getErrors()).hasSize(1)
                .satisfies(errors -> assertThat(errors.get(0).getMessage()).contains("Currency conversion failed"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoImps() {
        // Given
        BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();

        // When
        Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getMessage()).contains("No valid impressions"));
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorCurrency() {
        // Given
        BidRequest bidRequest = givenBidRequest(imp -> imp
                .bidfloor(BigDecimal.ONE)
                .bidfloorcur("EUR"));

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenReturn(BigDecimal.TEN);

        // When
        Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().get(0).getPayload().getImp())
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldUseDevEndpointWhenTestModeEnabled() {
        // Given
        BidRequest bidRequest = givenBidRequest(imp -> imp
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(true)))));

        // When
        Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo(DEV_ENDPOINT);
    }

    @Test
    public void makeHttpRequestsShouldAddUsdToCurrenciesIfMissing() {
        // Given
        BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder().cur(singletonList("EUR")).build();

        // When
        Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getValue().get(0).getPayload().getCur())
                .containsExactlyInAnyOrder("EUR", "USD");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyIsInvalid() {
        // Given
        BidderCall<BidRequest> httpCall = givenHttpCall();

        // When
        Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // Then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode");
                });
    }

    @Test
    public void makeBidsShouldReturnBidsWithCorrectTypes() throws JsonProcessingException {
        // Given
        ObjectNode bidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().put("type", "banner"));

        BidderCall<BidRequest> httpCall = givenHttpCall(
                BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder()
                                        .impid("123")
                                        .ext(bidExt)
                                        .build()))
                                .build()))
                        .build());

        // When
        Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // Then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void extractTestModeShouldReturnTrueWhenImpExtHasTestTrue() {
        // Given
        Imp imp = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(true))))
                .build();

        // When
        boolean testMode = target.extractTestMode(imp);

        // Then
        assertThat(testMode).isTrue();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")).build())))
                .build();
    }

    private BidderCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)),
                null);
    }

    private BidderCall<BidRequest> givenHttpCall() {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, "invalid"),
                null);
    }
}
