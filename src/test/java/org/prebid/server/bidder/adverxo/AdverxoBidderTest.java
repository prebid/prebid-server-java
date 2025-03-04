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
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
        );

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Error parsing ext.imp.bidder");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReplaceMacrosInEndpointUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/123/testAuth");
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfNeeded() {
        // given
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

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
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
    public void makeHttpRequestsShouldCreateMultipleRequestsForMultipleImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp("imp1"),
                        givenImp("imp2"),
                        givenImp("imp3")
                ))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode");
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
    public void makeBidsShouldResolveBannerBidType() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                Bid.builder().impid("123").mtype(1).adm("bannerAdm").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(), mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveVideoBidType() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                Bid.builder().impid("456").mtype(2).adm("videoAdm").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(), mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldResolveNativeBidType() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                Bid.builder().impid("789").mtype(4).adm("{\"native\":{\"assets\":[]}}").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(), mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .impid("999")
                .mtype(99)
                .adm("unsupportedAdm")
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Unsupported mType 99");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReplacePriceMacroInAdmAndNurl() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .impid("123")
                .mtype(1)
                .adm("Price is ${AUCTION_PRICE}")
                .price(BigDecimal.valueOf(5.55))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final BidderBid bidderBid = result.getValue().get(0);
        assertThat(bidderBid.getBid().getAdm()).isEqualTo("Price is 5.55");
    }

    @Test
    public void makeBidsShouldHandleNativeAdmParsing() throws JsonProcessingException {
        // given
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

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenNativeAdmIsInvalid() throws JsonProcessingException {
        // given
        final String invalidAdm = "{invalid_json";
        final Bid bid = Bid.builder()
                .impid("123")
                .mtype(4)
                .adm(invalidAdm)
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Error parsing native ADM");
        assertThat(result.getValue()).isEmpty();
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

    private static Imp givenImp(String impId) {
        return Imp.builder()
                .id(impId)
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdverxo.of(123, "testAuth"))))
                .build();
    }

    private static BidResponse givenBidResponse(Bid bid) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();
    }

}
