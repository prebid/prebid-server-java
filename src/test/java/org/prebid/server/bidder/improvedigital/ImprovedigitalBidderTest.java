package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ImprovedigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ImprovedigitalBidder improvedigitalBidder;

    @Before
    public void setUp() {
        improvedigitalBidder = new ImprovedigitalBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImprovedigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSplitMultipleImpRequest() {
        // given
        final List<Imp> imps = asList(
                givenImp(impBuilder -> impBuilder.id("123")),
                givenImp(impBuilder -> impBuilder.id("456")));

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = improvedigitalBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(imps.size())
                .extracting(HttpRequest::getPayload)
                .allSatisfy(request -> assertThat(request.getImp()).hasSize(1));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatBidsCountIsMoreThanOne() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(asList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build()
                )).build()));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unexpected SeatBid! Must be only one but have: 2"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBannerAndVideoAndNativeAreAbsent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unknown impression type for ID: \"123\""));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpNotFoundForId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123").xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("456"))));

        // when
        final Result<List<BidderBid>> result = improvedigitalBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Failed to find impression for ID: \"456\""));
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123")).build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
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
