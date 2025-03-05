package org.prebid.server.bidder.ogury;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OguryBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private OguryBidder target;

    @BeforeEach
    public void setUp() {
        target = new OguryBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void shouldFailOnInvalidEndpointUrl() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new OguryBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldEncodePassedBidRequest() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenReturn(BigDecimal.ONE);
        final BidRequest bidRequest = givenBidRequest();
        final BidRequest modifiedBidRequest = givenModifiedBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MultiMap expectedHeaders = HttpUtil.headers()
                .add(HttpUtil.USER_AGENT_HEADER, "ua")
                .add(HttpUtil.ACCEPT_LANGUAGE_HEADER, "en-US")
                .add(HttpUtil.X_FORWARDED_FOR_HEADER, "0.0.0.0")
                .add(HttpUtil.X_FORWARDED_FOR_HEADER, "ip6");
        final Result<List<HttpRequest<BidRequest>>> expectedResult = Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(ENDPOINT_URL)
                .headers(expectedHeaders)
                .impIds(Set.of("imp_id"))
                .body(jacksonMapper.encodeToBytes(modifiedBidRequest))
                .payload(modifiedBidRequest)
                .build());
        assertThat(result.getValue()).usingRecursiveComparison().isEqualTo(expectedResult.getValue());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestDoesNotHaveImpression() {
        // given
        final BidRequest bidrequest = givenBidRequest(bidRequest -> bidRequest.imp(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .contains(BidderError.badInput("There are no valid impressions to create bid request to ogury bidder"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestDoesNotHaveOguryKeys() {
        // given
        final BidRequest bidrequest = givenBidRequest(bidRequest -> bidRequest.imp(List.of(Imp.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidrequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .contains(BidderError.badInput(
                        "Invalid request. assetKey/adUnitId or request.site.publisher.id required"));
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseCodeIsBadRequest() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(HttpResponseStatus.BAD_REQUEST.code(), "invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .startsWith("Unexpected status code: 400. Run with request.debug = 1 for more info");
                });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseCodeIsNotOK() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(),
                "invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.generic);
                    assertThat(error.getMessage())
                            .startsWith("Unexpected status code: 500. Run with request.debug = 1 for more info");
                });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null);

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenResponseBodyIsWrong() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenBidMTypeIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Missing MType for impression: `123`"));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenBidMTypeIsNotSupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.impid("123").mtype(10)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unsupported MType '10', for impression '123'"));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(1)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithCurFromResponse() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.mtype(1)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("CUR");
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {

        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("CUR")
                .build());
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .id("id"))
                .build();
    }

    private BidRequest givenBidRequest() {
        final ObjectNode ogury = mapper.createObjectNode();
        ogury.putIfAbsent("adUnitId", TextNode.valueOf("1"));
        ogury.putIfAbsent("assetKey", TextNode.valueOf("key"));
        final ObjectNode bidder = mapper.createObjectNode();
        bidder.putIfAbsent("bidder", ogury);

        return givenBidRequest(bidRequest -> bidRequest.device(Device.builder()
                        .ua("ua")
                        .ip("0.0.0.0")
                        .ipv6("ip6")
                        .build())
                .imp(List.of(Imp.builder()
                        .id("imp_id")
                        .bidfloor(BigDecimal.TWO)
                        .bidfloorcur("CAD")
                        .ext(bidder)
                        .build())));
    }

    private BidRequest givenModifiedBidRequest() {
        final ObjectNode oguryKeys = mapper.createObjectNode();
        oguryKeys.putIfAbsent("adUnitId", TextNode.valueOf("1"));
        oguryKeys.putIfAbsent("assetKey", TextNode.valueOf("key"));

        return givenBidRequest(bidRequest -> bidRequest.device(Device.builder()
                        .ua("ua")
                        .ip("0.0.0.0")
                        .ipv6("ip6")
                        .build())
                .imp(List.of(Imp.builder()
                        .id("imp_id")
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("USD")
                        .tagid("imp_id")
                        .ext(oguryKeys)
                        .bidfloorcur("USD")
                        .build())));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return givenHttpCall(HttpResponseStatus.OK.code(), body);
    }

    private static BidderCall<BidRequest> givenHttpCall(int statusCode, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(statusCode, null, body),
                null);
    }
}
