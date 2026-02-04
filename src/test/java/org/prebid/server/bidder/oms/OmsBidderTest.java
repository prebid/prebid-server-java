package org.prebid.server.bidder.oms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.prebid.server.proto.openrtb.ext.request.omx.ExtImpOms;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badInput;

public class OmsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com?publisherId={{PublisherId}}";

    private final OmsBidder target = new OmsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OmsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first().isEqualTo(badInput("Invalid ext. Imp.Id: 123"));
    }

    @Test
    public void makeHttpRequestsShouldCreateUrlWithPidWhenPidIsProvided() {
        // given
        final ExtImpOms impExt = ExtImpOms.of("otherTagId", null);
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com?publisherId=otherTagId");
    }

    @Test
    public void makeHttpRequestsShouldCreateUrlWithPublisherIdWhenPublisherIdIsProvided() {
        // given
        final ExtImpOms impExt = ExtImpOms.of(null, 12345);
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer.ext(givenImpExt(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com?publisherId=12345");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, "invalid"),
                null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(impBuilder -> impBuilder.mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(impBuilder -> impBuilder.mtype(2)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnBannerWhenMTypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(impBuilder -> impBuilder.mtype(99)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidWithVideoInfo() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder()
                .mtype(2)
                .dur(30)
                .cat(List.of("IAB1"))
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(List.of(videoBid)).build()))
                .cur("USD")
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .containsExactly(ExtBidPrebidVideo.of(30, "IAB1"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")).build()))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)),
                null);
    }

    private ObjectNode givenImpExt(ExtImpOms impExt) {
        return mapper.valueToTree(ExtPrebid.of(null, impExt));
    }
}
