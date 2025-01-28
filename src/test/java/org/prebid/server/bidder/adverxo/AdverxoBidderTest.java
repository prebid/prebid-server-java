package org.prebid.server.bidder.adverxo;

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
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adverxo.ExtImpAdverxo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdverxoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/{{adUnitId}}/{{auth}}";
    private static final String AD_UNIT_ID = "123";
    private static final String AUTH = "testAuth";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private AdverxoBidder target;

    @BeforeEach
    public void setUp() {
        target = new AdverxoBidder(ENDPOINT_URL, jacksonMapper, currencyConversionService);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdverxoBidder(
                "invalid_url",
                jacksonMapper,
                currencyConversionService));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtInvalid() {
        // Given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
        );

        // When
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Error parsing ext.imp.bidder");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReplaceMacrosInEndpointUrl() {
        // Given
        final BidRequest bidRequest = givenBidRequest(identity());

        // When
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/123/testAuth");
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfNeeded() {
        // Given
        final BigDecimal bidFloor = BigDecimal.ONE;
        final String bidFloorCur = "EUR";
        final BigDecimal convertedPrice = BigDecimal.TEN;

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenReturn(convertedPrice);

        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder
                        .bidfloor(bidFloor)
                        .bidfloorcur(bidFloorCur)
        );

        // When
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // Then
        verify(currencyConversionService).convertCurrency(
                eq(bidFloor),
                any(),
                eq(bidFloorCur),
                eq("USD")
        );

        final BidRequest outgoingRequest = result.getValue().get(0).getPayload();
        final Imp modifiedImp = outgoingRequest.getImp().get(0);
        assertThat(modifiedImp.getBidfloor()).isEqualTo(convertedPrice);
        assertThat(modifiedImp.getBidfloorcur()).isEqualTo("USD");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // Given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // When
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // Then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode");
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // Given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // When
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // Then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldCorrectlyResolveBidTypes() throws JsonProcessingException {
        // Given
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(
                                Bid.builder().impid("123").mtype(1).adm("bannerAdm").build(),
                                Bid.builder().impid("456").mtype(2).adm("videoAdm").build(),
                                Bid.builder().impid("789").mtype(4).adm("{\"native\":{\"assets\":[]}}").build()))
                        .build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // When
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // Then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video, BidType.xNative);
    }

    @Test
    public void makeBidsShouldReplacePriceMacroInAdmAndNurl() throws JsonProcessingException {
        // Given
        final Bid bid = Bid.builder()
                .impid("123")
                .mtype(1)
                .adm("Price is ${AUCTION_PRICE}")
                .nurl("nurl?price=${AUCTION_PRICE}")
                .price(BigDecimal.valueOf(5.55))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // When
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // Then
        final BidderBid bidderBid = result.getValue().get(0);
        assertThat(bidderBid.getBid().getAdm()).isEqualTo("Price is 5.55");
        assertThat(bidderBid.getBid().getNurl()).isEqualTo("nurl?price=5.55");
    }

    @Test
    public void makeBidsShouldHandleNativeAdmParsing() throws JsonProcessingException {
        // Given
        final String adm = "{\"native\": {\"key\": \"value\"}}";
        final Bid bid = Bid.builder()
                .impid("123")
                .mtype(4)
                .adm(adm)
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // When
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // Then
        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo("{\"key\":\"value\"}");
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(List.of(impCustomizer.apply(Imp.builder()
                                        .id("123")
                                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdverxo.of(
                                                Integer.parseInt(AD_UNIT_ID), AUTH)))))
                                .build())))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
