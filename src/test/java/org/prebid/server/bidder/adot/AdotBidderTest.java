package org.prebid.server.bidder.adot;

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
import org.prebid.server.bidder.adot.model.AdotBidExt;
import org.prebid.server.bidder.adot.model.AdotExtAdot;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adot.ExtImpAdot;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AdotBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private AdotBidder adotBidder;

    @Before
    public void setUp() {
        adotBidder = new AdotBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdotBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingBidRequestAndPassItOn() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adotBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final List<HttpRequest<BidRequest>> httpRequests = result.getValue();
        assertThat(httpRequests)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
        assertThat(httpRequests)
                .extracting(HttpRequest::getPayload)
                .containsExactly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidExtAdotMediaTypeIsInvalid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse("invalid")));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenBidExtAdotMediaTypeIsVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse("video")));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid("video"), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenBidExtAdotMediaTypeIsBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse("banner")));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid("banner"), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenBidExtAdotMediaTypeIsNative() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse("native")));

        // when
        final Result<List<BidderBid>> result = adotBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid("native"), xNative, "USD"));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("firstImp")
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdot.of(true, "placementId")))))
                .build();
    }

    private static BidResponse givenBidResponse(String bidExtMediaType) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(givenBid(bidExtMediaType)))
                        .build()))
                .build();
    }

    private static Bid givenBid(String bidExtMediaType) {
        return Bid.builder()
                .ext(mapper.valueToTree(AdotBidExt.of(AdotExtAdot.of(bidExtMediaType))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
