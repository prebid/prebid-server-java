package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class CriteoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private CriteoBidder bidder;

    @Before
    public void setUp() {
        bidder = new CriteoBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new CriteoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldEncodePassedBidRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder().id("id").build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(httpRequest -> {
                    assertThat(httpRequest.getBody()).isEqualTo(jacksonMapper.encodeToBytes(bidRequest));
                    assertThat(httpRequest.getPayload()).isSameAs(bidRequest);
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtPrebidTypeIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Missing ext.prebid.type in bid for impression : 123."));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.ext(givenBidExt(BidType.banner)))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.ext(givenBidExt(BidType.video)))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnBidWithCurFromResponse() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bid -> bid.ext(givenBidExt(BidType.banner)))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("CUR");
    }

    @Test
    public void makeBidsShouldReturnBidWithNetworkNameFromExtPrebid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(
                        bid -> bid
                                .impid("123")
                                .ext(givenBidExtWithNetwork("anyNetworkName")))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("meta"))
                .containsExactly(mapper.createObjectNode().put("networkName", "anyNetworkName"));
    }

    @Test
    public void makeBidsShouldReturnEmptyNetworkNameWhenBidExtPrebidNotContainNetworkName()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(
                        bid -> bid.ext(givenBidExtWithNetwork(null)))));

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("meta"))
                .doesNotContain(mapper.createObjectNode().put("networkName", "anyNetworkName"));
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("CUR")
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static ObjectNode givenBidExt(BidType bidType) {
        final ObjectNode ext = mapper.createObjectNode();
        ext.putObject("prebid").put("type", bidType.getName());
        return ext;
    }

    private static ObjectNode givenBidExtWithNetwork(String networkNameValue) {
        final ObjectNode ext = mapper.createObjectNode();
        final ObjectNode prebid = ext.putObject("prebid");
        prebid.put("type", BidType.banner.getName());
        prebid.put("networkName", networkNameValue);
        return ext;
    }

}
