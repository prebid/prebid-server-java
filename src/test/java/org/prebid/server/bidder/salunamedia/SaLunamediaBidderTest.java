package org.prebid.server.bidder.salunamedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class SaLunamediaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com";

    private SaLunamediaBidder saLunamediaBidder;

    @Before
    public void setUp() {
        saLunamediaBidder = new SaLunamediaBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SaLunamediaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyOneRequestForAllImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("123").banner(Banner.builder().build()).build(),
                        Imp.builder().id("456").video(Video.builder().build()).build()))
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> requests = saLunamediaBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(requests.getErrors()).isEmpty();
        assertThat(requests.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseFirstSeatBidHasEmptyBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder().seatbid(singletonList(null)).build()));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid.Bids"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseFirstBidOfFirstSeatBidHasEmptyExt()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Missing BidExt"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseFirstBidHasInvalidMediaType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponses(singletonList(bidBuilder -> bidBuilder.impid("123").ext(
                        jacksonMapper.mapper().createObjectNode().put("mediaType", "invalid_type "))))));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
                });
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfExtMediatypeIsNative() throws JsonProcessingException {
        // given
        final ObjectNode mediaTypeObjectNode = jacksonMapper.mapper()
                .createObjectNode().put("mediaType", "native");
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponses(asList(
                        bidBuilder -> bidBuilder.impid("123").ext(mediaTypeObjectNode),
                        bidBuilder -> bidBuilder.impid("345").ext(mediaTypeObjectNode)))));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").ext(mediaTypeObjectNode).build(), xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfExtMediatypeIsBanner() throws JsonProcessingException {
        // given
        final ObjectNode mediaTypeObjectNode = jacksonMapper.mapper()
                .createObjectNode().put("mediaType", "banner");
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponses(asList(
                        bidBuilder -> bidBuilder.impid("123").ext(mediaTypeObjectNode),
                        bidBuilder -> bidBuilder.impid("345").ext(mediaTypeObjectNode)))));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").ext(mediaTypeObjectNode).build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfExtMediatypeIsVideo() throws JsonProcessingException {
        // given
        final ObjectNode mediaTypeObjectNode = jacksonMapper.mapper()
                .createObjectNode().put("mediaType", "video");
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponses(asList(
                        bidBuilder -> bidBuilder.impid("123").ext(mediaTypeObjectNode),
                        bidBuilder -> bidBuilder.impid("345").ext(mediaTypeObjectNode)))));

        // when
        final Result<List<BidderBid>> result = saLunamediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").ext(mediaTypeObjectNode).build(), video, null));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponses(List<Function<Bid.BidBuilder, Bid.BidBuilder>> bidCustomizers) {
        return BidResponse.builder()
                .seatbid(bidCustomizers.stream().map(customizer -> SeatBid.builder()
                        .bid(singletonList(customizer.apply(Bid.builder()).build()))
                        .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
