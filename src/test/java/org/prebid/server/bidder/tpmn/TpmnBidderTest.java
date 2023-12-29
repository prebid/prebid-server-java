package org.prebid.server.bidder.tpmn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.bidder.frvradn.model.ExtImpFrvrAdn;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class TpmnBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CurrencyConversionService currencyConversionService;

    private TpmnBidder target;

    @Before
    public void setUp() {
        target = new TpmnBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TpmnBidder(
                "invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com");
    }

    @Test
    public void makeHttpRequestsShouldConvertCurrencyIfRequestCurrencyDoesNotMatchBidderCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("EUR"));

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
    public void makeHttpRequestsShouldCreateRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123"),
                impBuilder -> impBuilder.id("456"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
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
    public void makeBidsShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(1).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(2).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().mtype(2).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMTypeIsFour() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(4).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().mtype(4).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatDoesNotContainMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(givenImp(identity()))).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().id("123").build(),
                        Bid.builder().mtype(1).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Missing MType for bid: 123");
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatDoesNotContainSupportedMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(givenImp(identity()))).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(1).build(),
                        Bid.builder().id("123").mtype(3).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unsupported MType: 3, for bid: 123");
    }

    private BidRequest givenBidRequest() {
        return givenBidRequest(impBuilder -> impBuilder.id("123"));
    }

    private BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(this::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(
                Imp.builder().ext(givenImpExt("publisher_id", "ad_unit_id"))).build();
    }

    private ObjectNode givenImpExt(String publisherId, String adUnitId) {
        return mapper.valueToTree(
                ExtPrebid.of(null, ExtImpFrvrAdn.of(publisherId, adUnitId)));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(List.of(bids))
                        .build()))
                .build();
    }

    private static ObjectNode givenBidExt(String value) {
        return mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().set("type", TextNode.valueOf(value)));
    }

    private static Bid givenBid(String type) {
        return Bid.builder().impid("123").ext(givenBidExt(type)).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
