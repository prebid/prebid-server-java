package org.prebid.server.bidder.lemmadigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.lemmadigital.ExtImpLemmaDigital;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class LemmaDigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/{{AdUnit}}/{{PublisherID}}";

    private final LemmaDigitalBidder target = new LemmaDigitalBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LemmaDigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestedBidRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(identity()), givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsOnly(bidRequest);
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getImpIds)
                .containsOnly(Set.of("123"));
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpAndReturnCorrectUrlWhenRequestContainTwoImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpLemmaDigital.of(5, 23))))),
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpLemmaDigital.of(3, 12)))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/23/5");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestWithoutImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(null).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .containsExactly(BidderError.badInput("Impression array should not be empty"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenFirstImpDoesNotContainExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .containsExactly(BidderError.badInput("Missing bidder ext in impression with id: 123"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithBannerWhenMediaTypeIsNotSpecified() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithVideoWhenBannerAndVideoInBidRequestSpecified()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall,
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())
                        .video(Video.builder().build())));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, null));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpLemmaDigital.of(5, 23)))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder().impid("123")).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
