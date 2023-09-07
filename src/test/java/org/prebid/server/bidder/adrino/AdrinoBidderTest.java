package org.prebid.server.bidder.adrino;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adrino.ExtImpAdrino;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.headers;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class AdrinoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://prd-prebid-bidder.adrino.io/openrtb/bid";

    private final AdrinoBidder target = new AdrinoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("test-request-id")
                .imp(List.of(Imp.builder().id("imp_id").build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MultiMap expectedHeaders = headers()
                .set(CONTENT_TYPE_HEADER, APPLICATION_JSON_CONTENT_TYPE)
                .set(ACCEPT_HEADER, APPLICATION_JSON_VALUE);
        final Result<List<HttpRequest<BidRequest>>> expectedResult = Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(ENDPOINT_URL)
                .headers(expectedHeaders)
                .impIds(Set.of("imp_id"))
                .body(jacksonMapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build());
        assertThat(result.getValue()).usingRecursiveComparison().isEqualTo(expectedResult.getValue());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnSingleHttpRequestsWhenTwoImpsHasDifferentSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build(),
                        Imp.builder()
                                .xNative(Native.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdrino.of("test"))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("impId").build();
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .cur("PLN")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bid))
                        .build()))
                .build());

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).element(0).satisfies(bidderBid -> {
            assertThat(bidderBid.getBid()).isEqualTo(bid);
            assertThat(bidderBid.getType()).isEqualTo(BidType.xNative);
            assertThat(bidderBid.getBidCurrency()).isEqualTo("PLN");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseWhenWhereIsNoBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(null);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsFromDifferentSeatBidsInResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId2").impid("impId2").build()))
                                .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").build(), Imp.builder().id("impId2").build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(response);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).satisfies(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
        });
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(204, null, body), null);
    }
}
