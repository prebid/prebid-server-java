package org.prebid.server.bidder.liftoff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
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
import org.prebid.server.bidder.liftoff.model.LiftoffImpressionExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.liftoff.ExtImpLiftoff;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class LiftoffBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    private LiftoffBidder target;

    @Before
    public void setUp() {
        target = new LiftoffBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LiftoffBidder(
                "invalid_url",
                currencyConversionService,
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldSuccessfullyPassed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConvertAndReturnProperBidFloorCur() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder.bidfloorcur("EUR")
                .bidfloor(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .first()
                .satisfies(imps -> {
                    assertThat(imps.getBidfloorcur()).isEqualTo("USD");
                    assertThat(imps.getBidfloor()).isEqualTo(BigDecimal.ONE);
                });
    }

    @Test
    public void makeHttpRequestsShouldNotConvertAndReturnProperBidFloorCurWhenBidFloorCurAlreadyUSD() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.bidfloorcur("USD")
                        .bidfloor(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isEqualTo("USD");
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.TEN);
                });
    }

    @Test
    public void makeHttpRequestsShouldNotConvertAndReturnProperBidFloorCurWhenBidFloorNotPositiveNumber() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.ONE);

        final BidRequest bidRequest = givenBidRequest(identity(), impBuilder -> impBuilder.bidfloorcur("EUR")
                .bidfloor(BigDecimal.ZERO));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .first()
                .satisfies(imp -> {
                    assertThat(imp.getBidfloorcur()).isEqualTo("EUR");
                    assertThat(imp.getBidfloor()).isEqualTo(BigDecimal.ZERO);
                });
    }

    @Test
    public void makeHttpRequestsShouldThrowErrorWhenCurrencyConvertCannotConvertInAnotherCurrency() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenThrow(
                new PreBidException("Unable to convert from currency UAH to desired ad server currency USD"));

        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.bidfloorcur("UAH").bidfloor(BigDecimal.TEN));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to convert from currency UAH to desired ad server currency USD");
    }

    @Test
    public void makeHttpRequestShouldUpdateExtImpLiftoffWhenUserBuyeruidPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.user(User.builder().buyeruid("123").build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(LiftoffImpressionExt.builder()
                        .bidder(ExtImpLiftoff.builder()
                                .bidToken("any-bid-token")
                                .appStoreId("any-app-store-id")
                                .placementReferenceId("any-placement-reference-id")
                                .build())
                        .vungle(ExtImpLiftoff.builder()
                                .bidToken("123")
                                .appStoreId("any-app-store-id")
                                .placementReferenceId("any-placement-reference-id")
                                .build())
                        .build(), ObjectNode.class));
    }

    @Test
    public void makeHttpRequestShouldUpdateAppIdWhenExtImpLiftoffContainAppStoreId() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getId)
                .containsExactly("any-app-store-id");
    }

    @Test
    public void makeHttpRequestShouldUpdateImpTagidWhenExtImpLiftoffContainPlacementReferenceId() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("any-placement-reference-id");
    }

    @Test
    public void makeHttpRequestShouldUpdateExtImpLiftoffBidTokenWhenInRequestPresentUserBuyeruid() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.user(User.builder().buyeruid("Came-from-request").build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(LiftoffImpressionExt.builder()
                        .bidder(ExtImpLiftoff.builder()
                                .bidToken("any-bid-token")
                                .appStoreId("any-app-store-id")
                                .placementReferenceId("any-placement-reference-id")
                                .build())
                        .vungle(ExtImpLiftoff.builder()
                                .bidToken("Came-from-request")
                                .appStoreId("any-app-store-id")
                                .placementReferenceId("any-placement-reference-id")
                                .build())
                        .build(), ObjectNode.class));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

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
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfBaneerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, null));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder>... impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(Arrays.stream(impCustomizer).map(LiftoffBidderTest::givenImp).toList()))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(23).h(25).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLiftoff.builder()
                                .bidToken("any-bid-token")
                                .appStoreId("any-app-store-id")
                                .placementReferenceId("any-placement-reference-id")
                                .build()))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
