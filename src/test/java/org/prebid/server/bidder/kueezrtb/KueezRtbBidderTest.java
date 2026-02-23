package org.prebid.server.bidder.kueezrtb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kueezrtb.KueezRtbImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;

class KueezRtbBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.host.com/prebid/bid/";

    private final KueezRtbBidder target = new KueezRtbBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpoint() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KueezRtbBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final Imp imp1 = givenImp(imp -> imp.id("imp1"));
        final Imp imp2 = givenImp(imp -> imp.id("imp2"));
        final BidRequest request = BidRequest.builder().imp(List.of(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactlyInAnyOrder(imp1, imp2);
    }

    @Test
    public void makeHttpRequestsShouldContainExpectedHeaders() {
        // given
        final BidRequest request = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE));
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.host.com/prebid/bid/cid");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnInvalidImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp ->
                imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyInvalid() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWithTypeSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("imp1").mtype(1).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWithTypeSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().impid("imp2").mtype(2).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().id("bidId3").impid("id3").mtype(3).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).containsOnly(badServerResponse("Could not define bid type for imp: id3"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsNotIncluded() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().id("bidId1").impid("id1").mtype(1).build();
        final Bid bidWithoutMtype = Bid.builder().id("bidId2").impid("id2").mtype(null).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bannerBid, bidWithoutMtype));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).containsOnly(badServerResponse("Could not define bid type for imp: id2"));
        assertThat(result.getValue()).containsOnly(BidderBid.of(bannerBid, banner, "USD"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(singletonList(givenImp(impCustomizer)))).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("impId")
                .ext(mapper.valueToTree(ExtPrebid.of(null, KueezRtbImpExt.of("cid")))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }
}
