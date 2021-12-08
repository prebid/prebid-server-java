package org.prebid.server.bidder.nextmillenium;

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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillenium.ExtImpNextMillenium;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class NextMilleniumBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/";

    private NextMilleniumBidder nextMilleniumBidder;

    @Before
    public void setUp() {
        nextMilleniumBidder = new NextMilleniumBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NextMilleniumBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectResult() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nextMilleniumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .containsExactly(mapper.writeValueAsBytes(
                        BidRequest.builder()
                                .id(bidRequest.getId())
                                .test(bidRequest.getTest())
                                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of("placement_id"))
                                        .build()))
                                .build()));
    }

    @Test
    public void makeHttpRequestsWithProperDataShouldContainCorrectPayload() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nextMilleniumBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedPayload = BidRequest.builder()
                .id(bidRequest.getId())
                .test(bidRequest.getTest())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("placement_id"))
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedPayload);
    }

    @Test
    public void makeHttpRequestsWithInvalidImpsShouldReturnError() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = nextMilleniumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value of type");
                });
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = nextMilleniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), BidType.banner, "USD"));
    }

    @Test
    public void makeBidsWithZeroSeatBidsShouldReturnNoErrorsAndNoValues() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(Collections.emptyList())
                        .build()));

        // when
        final Result<List<BidderBid>> result = nextMilleniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsWithUnparsableBidResponseShouldReturnError() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.createArrayNode().toString());

        // when
        final Result<List<BidderBid>> result = nextMilleniumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty()
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(bidderError.getMessage()).startsWith("Failed to decode:");
                });

    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .id("1500")
                        .test(100)
                        .imp(Collections.singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpNextMillenium.of("placement_id")))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(Collections.singletonList(SeatBid.builder()
                        .bid(Collections.singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("USD")
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
