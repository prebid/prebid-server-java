package org.prebid.server.bidder.aduptech;

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
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class AduptechBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private static final String TARGET_CURRENCY = "EUR";

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;

    private AduptechBidder target;

    @BeforeEach
    public void setUp() {
        target = new AduptechBidder(ENDPOINT_URL, jacksonMapper, currencyConversionService, TARGET_CURRENCY);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AduptechBidder("invalid_url",
                        jacksonMapper,
                        currencyConversionService,
                        TARGET_CURRENCY));
    }

    @Test
    public void creationShouldFailOnInvalidTargetCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AduptechBidder(ENDPOINT_URL,
                        jacksonMapper,
                        currencyConversionService,
                        "invalid_currency"))
                .withMessage("invalid extra info: invalid TargetCurrency invalid_currency");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfCurrencyConversionFails() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("USD"));

        given(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .willThrow(new PreBidException("test-error"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsExactly(badInput("test-error"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorIfCurrencyIsDifferent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("USD"));

        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "USD", TARGET_CURRENCY))
                .willReturn(BigDecimal.valueOf(12.0));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.valueOf(12.0), TARGET_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldPerformTwoStepCurrencyConversionIfInitialConversionFails() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.bidfloor(BigDecimal.TEN).bidfloorcur("CAD"));

        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "CAD", TARGET_CURRENCY))
                .willThrow(new PreBidException("initial conversion failed"));
        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "CAD", "USD"))
                .willReturn(BigDecimal.valueOf(8));
        given(currencyConversionService.convertCurrency(BigDecimal.valueOf(8), bidRequest, "USD", TARGET_CURRENCY))
                .willReturn(BigDecimal.valueOf(9.6));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.valueOf(9.6), TARGET_CURRENCY));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpIfBidFloorIsNotValid() {
        // given
        final Imp imp = givenImp(builder -> builder.bidfloor(BigDecimal.ZERO).bidfloorcur("USD"));
        final BidRequest bidRequest = givenBidRequest(imp);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(imp);

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpIfBidFloorCurrencyIsSameAsTarget() {
        // given
        final Imp imp = givenImp(builder -> builder.bidfloor(BigDecimal.TEN).bidfloorcur(TARGET_CURRENCY));
        final BidRequest bidRequest = givenBidRequest(imp);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(imp);

        verifyNoInteractions(currencyConversionService);
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestWithAllImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1"),
                imp -> imp.id("imp2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getImpIds)
                .containsExactly(Set.of("imp1", "imp2"));
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
                .satisfies(headers -> assertThat(headers.get("Componentid"))
                        .isEqualTo("prebid-java"))
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedEndpointUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo(ENDPOINT_URL);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(4);
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(2)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Unknown markup type: 2"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().imp(List.of(imps)).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(AduptechBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("impId")).build();
    }

    private static Bid givenBid(Integer mtype) {
        return Bid.builder().mtype(mtype).build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
