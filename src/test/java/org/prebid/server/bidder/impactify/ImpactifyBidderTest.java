package org.prebid.server.bidder.impactify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.impactify.ExtImpImpactify;
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

public class ImpactifyBidderTest extends VertxTest {

    private static final String TEST_ENDPOINT = "https://test.endpoint.com";
    private static final String INCORRECT_TEST_ENDPOINT = "incorrect.endpoint";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ImpactifyBidder impactifyBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        impactifyBidder = new ImpactifyBidder(TEST_ENDPOINT, jacksonMapper, currencyConversionService);
    }

    //TODO: ADD ZERO PRICE TO CHECK IF PRICE IS VALID
    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImpactifyBidder(INCORRECT_TEST_ENDPOINT,
                jacksonMapper, currencyConversionService));
    }

    private static Imp givenImpressionWithValidData() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static Imp givenImpressionWithZeroPrice() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ZERO)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }


    private static Imp givenImpressionWithNonBidderCurrency() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("EUR")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    @Test
    public void makeHttpRequestsShouldCheckIfValidDataInImpressionHasCorrectBidFloorAndBidFloorCur() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidData()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, "USD"));
    }

    @Test
    public void makeHttpRequestsWithOneImpressionWithZeroPriceAddsNoValidImpressionsError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithZeroPrice()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .isEqualTo("Unable to decode the impression ext for id: 123");

    }

    @Test
    public void makeHttpRequestsShouldCheckIfImpressionHasCorrectBidFloorAndBidFloorCurChangeItOtherwise() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithNonBidderCurrency()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeBidsShouldReturnValidBidResponse() throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidData()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsOnly(Bid.builder()
                        .impid("123")
                        .build());

        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);


    }
}
