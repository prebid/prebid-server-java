package org.prebid.server.bidder.evolution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class EvolutionBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://service.e-volution.ai/pbserver";

    private EvolutionBidder evolutionBidder;

    @Before
    public void setUp() {
        evolutionBidder = new EvolutionBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new EvolutionBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyOneRequestForAllImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("123").banner(Banner.builder().build()).build(),
                        Imp.builder().id("456").video(Video.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> requests = evolutionBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(requests.getErrors()).isEmpty();
        assertThat(requests.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = evolutionBidder.makeBids(httpCall, null);

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
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = evolutionBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty seatbid"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = evolutionBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty seatbid"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = evolutionBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty seatbid"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsWithValidTypes() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().build(),
                mapper.writeValueAsString(
                        givenBidResponse(
                                givenBid("123", banner),
                                givenBid("345", video),
                                givenBid("456", audio),
                                givenBid("567", xNative),
                                givenBid("789", "invalid_type"))));

        // when
        final Result<List<BidderBid>> result = evolutionBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(
                        BidderBid.of(givenBid("123", banner), banner, null),
                        BidderBid.of(givenBid("345", video), video, null),
                        BidderBid.of(givenBid("456", audio), audio, null),
                        BidderBid.of(givenBid("567", xNative), xNative, null),
                        BidderBid.of(givenBid("789", "invalid_type"), banner, null));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .build();
    }

    private static Bid givenBid(String impid, BidType bidType) {
        return Bid.builder().impid(impid).ext(
                mapper.createObjectNode().put("mediaType", bidType.getName())).build();
    }

    private static Bid givenBid(String impid, String bidType) {
        return Bid.builder().impid(impid).ext(mapper.createObjectNode().put("mediaType", bidType)).build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
