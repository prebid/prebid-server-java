package org.prebid.server.bidder.medianet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class MedianetBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.media.net?src={{PREBID_SERVER_ENDPOINT}}";
    private static final String EXTERNAL_URL = "https://external.prebidserver.com";
    private static final BidRequest DUMMY_REQUEST = BidRequest.builder()
            .id("request_id")
            .imp(singletonList(Imp.builder()
                .id("imp_id")
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                .build()))
            .build();

    private MedianetBidder medianetBidder;

    @Before
    public void setup() {
        medianetBidder = new MedianetBidder(ENDPOINT_URL, jacksonMapper, EXTERNAL_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MedianetBidder("invalid_url", jacksonMapper, EXTERNAL_URL));
    }

    @Test
    public void httpRequestShouldContainCorrectUrl() {
        final Result<List<HttpRequest<BidRequest>>> result;
        result = medianetBidder.makeHttpRequests(DUMMY_REQUEST);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
            .extracting(HttpRequest::getUri)
            .containsExactly("https://test.media.net?src=https%3A%2F%2Fexternal.prebidserver.com");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        final Result<List<HttpRequest<BidRequest>>> result;
        result = medianetBidder.makeHttpRequests(DUMMY_REQUEST);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
            .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
            .containsOnly(DUMMY_REQUEST);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        final HttpCall<BidRequest> httpCall = sampleHttpCall(DUMMY_REQUEST, "invalid response");
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, null);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        final HttpCall<BidRequest> httpCall;
        httpCall = sampleHttpCall(DUMMY_REQUEST, mapper.writeValueAsString(null));
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, null);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        final HttpCall<BidRequest> httpCall;
        httpCall = sampleHttpCall(null, mapper.writeValueAsString(BidResponse.builder().build()));
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, null);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                DUMMY_REQUEST,
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, null);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    private static BidResponse sampleBidResponse(Function<Bid.BidBuilder,
            Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
            .cur("USD")
            .seatbid(singletonList(SeatBid.builder()
                .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                .build()))
            .build();
    }

    private static HttpCall<BidRequest> sampleHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
            HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
            HttpResponse.of(200, null, body),
            null);
    }
}
