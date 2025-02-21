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
import java.util.Collections;
import java.util.List;
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
    private static final String DEV_ENDPOINT = "https://bid-service.dev.essrtb.com/bid/prebid_server_rtb_call";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String EXT_PREBID = "prebid";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private KoblerBidder target;

    @BeforeEach
    public void setUp() {
        target = new KoblerBidder(
                ENDPOINT_URL,
                DEFAULT_BID_CURRENCY,
                EXT_PREBID,
                DEV_ENDPOINT,
                currencyConversionService,
                jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new KoblerBidder(
                        "invalid_url",
                        DEFAULT_BID_CURRENCY,
                        EXT_PREBID,
                        DEV_ENDPOINT,
                        currencyConversionService,
                        jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .imp(singletonList(Imp.builder()
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("EUR")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(false)))).build())).build();

        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenThrow(new PreBidException("Currency conversion failed"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactlyInAnyOrder("Currency conversion failed");
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorCurrency() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .cur(singletonList("EUR")),
                imp -> imp.bidfloor(BigDecimal.ONE).bidfloorcur("EUR")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(false)))));

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
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                        .cur(singletonList("EUR")),
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(true)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo(DEV_ENDPOINT);
    }

    @Test
    public void makeHttpRequestsShouldAddUsdToCurrenciesIfMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp ->
                imp.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpKobler.of(false))))).toBuilder()
                .cur(singletonList("EUR")).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getPayload().getCur()).containsExactlyInAnyOrder("EUR", "USD");
    }

    @Test
    public void makeHttpRequestsShouldUseDevEndpointwhenImpExtTestIsTrue() {
        // given
        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.putObject("bidder").put("test", true);

        final Imp imp = Imp.builder().id("test-imp").ext(extNode).build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp)).cur(Collections.singletonList("USD")).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final HttpRequest<BidRequest> httpRequest = result.getValue().get(0);
        assertThat(httpRequest.getUri()).isEqualTo(DEV_ENDPOINT);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyIsInvalid() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnBidsWithCorrectTypes() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().put("type", "banner"));

        final BidderCall<BidRequest> httpCall = givenHttpCall(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("123").ext(bidExt).build())).build())).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnEmptyListwhenBidResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListwhenSeatbidIsEmpty() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(Collections.emptyList())
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")).build()))).build();
    }

    private BidderCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)), null);
    }

    private BidderCall<BidRequest> givenHttpCall() {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, "invalid"), null);
    }

    private BidderCall<BidRequest> givenHttpCallWithBody(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
