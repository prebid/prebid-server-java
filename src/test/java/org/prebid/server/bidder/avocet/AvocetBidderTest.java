package org.prebid.server.bidder.avocet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.avocet.model.AvocetBidExtension;
import org.prebid.server.bidder.avocet.model.AvocetResponseExt;
import org.prebid.server.bidder.kubient.KubientBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class AvocetBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private AvocetBidder avocetBidder;

    @Before
    public void setUp() {
        avocetBidder = new AvocetBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KubientBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = avocetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                        .ext(mapper.createObjectNode().put("avocet", "invalid")))));
        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsOnly(
                BidderError.badServerResponse("Invalid Avocet bidder bid extension"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfDurationIsZeroAndApiIsThree() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").api(3)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0)))))));

        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                                .impid("123")
                                .api(3)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0))))
                                .build(),
                        banner,
                        null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfDurationIsZeroAndApiIsTwo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").api(2)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0)))))));

        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                                .impid("123")
                                .api(2)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0))))
                                .build(),
                        video,
                        null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfDurationIsZeroAndApiIsOne() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").api(1)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0)))))));

        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                                .impid("123")
                                .api(1)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(0))))
                                .build(),
                        video,
                        null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfDurationIsNotZero() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").api(3)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(1)))))));

        // when
        final Result<List<BidderBid>> result = avocetBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder()
                                .impid("123")
                                .api(3)
                                .ext(mapper.valueToTree(AvocetResponseExt.of(AvocetBidExtension.of(1))))
                                .build(),
                        video,
                        null));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(avocetBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
