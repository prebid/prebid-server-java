package org.prebid.server.bidder.rise;

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
import org.prebid.server.proto.openrtb.ext.request.rise.ExtImpRise;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class RiseBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final RiseBidder target = new RiseBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RiseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldThrowErrorWhenExtImpOrgAndPublisherAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRise.of(null, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("No org or publisher_id supplied");
                });
    }

    @Test
    public void makeHttpRequestsShouldPasteValueFromExtImpOrgToURLEndpointWhenPublisherIdPresentAndOrgAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRise.of("  testPublisherId  ", null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com?publisher_id=testPublisherId");
    }

    @Test
    public void makeHttpRequestsShouldPasteValueFromExtImpOrgToURLEndpointWhenOrgPresentAndPublisherIdAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRise.of(null, " testOrg  "))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com?publisher_id=testOrg");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
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
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMTypeIsOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(1).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfMTypeIsTwo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(Imp.builder().id("123").build())).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(2).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().mtype(2).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatDoesNotContainMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(givenImp(identity()))).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().id("123").build(),
                        Bid.builder().mtype(1).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Missing MType for bid: 123");
    }

    @Test
    public void makeBidsShouldReturnErrorsForBidsThatDoesNotContainSupportedMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder().imp(singletonList(givenImp(identity()))).build(),
                mapper.writeValueAsString(givenBidResponse(Bid.builder().mtype(1).build(),
                        Bid.builder().id("123").mtype(3).build())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(Bid.builder().mtype(1).build(), banner, "USD"));
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unsupported MType: 3, for bid: 123");
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {

        return requestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("123"))
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRise.of("testPublisherId", null))))
                .build();
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(List.of(bids))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }
}
