package org.prebid.server.bidder.beop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.beop.ExtImpBeop;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class BeopBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";

    private final BeopBidder target = new BeopBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BeopBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasNoImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No impressions provided"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .allMatch(message -> message.startsWith("ext.bidder not provided:"));
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithPidInUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ExtImpBeop.of("publisherId", null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?pid=publisherId");
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithNidAndNptnidInUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ExtImpBeop.of(null, "networkId", "partnerId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?nid=networkId&nptnid=partnerId");
    }

    @Test
    public void makeHttpRequestsShouldAcceptLegacyNtpnidAlias() {
        // given
        final ObjectNode bidderNode = mapper.createObjectNode()
                .put("nid", "networkId")
                .put("ntpnid", "legacyPartnerId");
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("bidder", bidderNode);

        final Imp imp = Imp.builder()
                .id("imp-1")
                .ext(ext)
                .build();
        final BidRequest bidRequest = givenBidRequest(imp);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?nid=networkId&nptnid=legacyPartnerId");
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithOriginalPayload() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ExtImpBeop.of("publisherId", null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(Bid.builder().impid("imp-1").mtype(1).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(Bid.builder().impid("imp-1").mtype(2).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorForBidWithoutMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(Bid.builder().impid("imp-1").build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mtype for impression \"imp-1\"");
    }

    @Test
    public void makeBidsShouldReturnErrorForBidWithUnsupportedMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(Bid.builder().impid("imp-1").mtype(3).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mtype for impression \"imp-1\"");
    }

    @Test
    public void makeBidsShouldReturnValidBidsAndErrorsForMixedMTypes() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(
                        Bid.builder().impid("imp-1").mtype(1).build(),
                        Bid.builder().impid("imp-2").mtype(2).build(),
                        Bid.builder().impid("imp-3").mtype(3).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner, BidType.video);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Failed to parse bid mtype for impression \"imp-3\"");
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().imp(List.of(imps)).build();
    }

    private static Imp givenImp(ExtImpBeop extImpBeop) {
        return Imp.builder()
                .id("imp-1")
                .ext(mapper.valueToTree(ExtPrebid.of(null, extImpBeop)))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bids))
                        .build()))
                .build();
    }
}
