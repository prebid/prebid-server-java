package org.prebid.server.bidder.adpone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Java6Assertions.assertThat;

public class AdponeBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.com/bid-request?src=prebid_server";

    private AdponeBidder adponeBidder;

    @Before
    public void setUp() {
        adponeBidder = new AdponeBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdponeBidder("invalid", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfFirstImpExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(mapper.createArrayNode());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adponeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();

        final List<BidderError> errors = result.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingBidRequestAndPassItOn() {
        // given
        final BidRequest bidRequest = givenBidRequest(mapper.createObjectNode());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adponeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        assertThat(httpRequests).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
        assertThat(httpRequests.get(0).getPayload()).isSameAs(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldFillExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(mapper.createObjectNode());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adponeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"),
                        tuple("x-openrtb-version", "2.5"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = adponeBidder.makeBids(httpCall, null);

        // then
        Assertions.assertThat(result.getErrors()).hasSize(1);
        Assertions.assertThat(result.getErrors().get(0).getMessage())
                .startsWith("Failed to decode: Unrecognized token");
        Assertions.assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        Assertions.assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = adponeBidder.makeBids(httpCall, null);

        // then
        Assertions.assertThat(result.getErrors()).isEmpty();
        Assertions.assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = adponeBidder.makeBids(httpCall, null);

        // then
        Assertions.assertThat(result.getErrors()).isEmpty();
        Assertions.assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnReturnBannerBid() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId").build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(bid))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = adponeBidder.makeBids(httpCall, null);

        // then
        Assertions.assertThat(result.getErrors()).isEmpty();
        Assertions.assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(adponeBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(Collections.emptyMap());
    }

    private static BidRequest givenBidRequest(JsonNode bidderNode) {
        return BidRequest.builder()
                .imp(Collections.singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, bidderNode)))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
