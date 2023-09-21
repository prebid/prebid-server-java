package org.prebid.server.bidder.definemedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class DefineMediaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com";

    private final DefineMediaBidder target = new DefineMediaBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DefineMediaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com");
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseInvalid() {
        // given
        final BidderCall<BidRequest> response = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfSeatBidNullOrEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> response = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldExtractBannerBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getType)
                .containsExactly(BidType.banner);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldExtractNativeBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.xNative).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldNotExtractVideoBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.video).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Invalid mediatype: video in the impression null"));
    }

    @Test
    public void makeBidsShouldNotExtractAudioBidTypeFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.audio).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Invalid mediatype: audio in the impression null"));
    }

    @Test
    public void makeBidsShouldUseBidResponseCur() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue())
                .flatExtracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCollectErrorIfBidTypeInvalid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(BidType.banner).build());
        final BidderCall<BidRequest> response = givenHttpCall(givenBidResponse(
                givenBid(bid -> bid.impid("impId")),
                givenBid(bid -> bid.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Invalid mediatype: null in the impression impId"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")).build())))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse response) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(response));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }
}
