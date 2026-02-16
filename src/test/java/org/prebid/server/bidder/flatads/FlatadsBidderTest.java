package org.prebid.server.bidder.flatads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.flatads.ExtImpFlatads;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class FlatadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://endpoint.com/?publisher={{PublisherID}}&token={{TokenID}}";

    private final FlatadsBidder target = new FlatadsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FlatadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Failed to deserialize Flatads extension:");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSeparateRequestForEachImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("imp1"),
                imp -> imp.id("imp2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactly(Set.of("imp1"), Set.of("imp2"));
    }

    @Test
    public void makeHttpRequestsShouldResolveEndpointMacrosCorrectly() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("testPublisher", "testToken")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://endpoint.com/?publisher=testPublisher&token=testToken");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(Device.builder()
                        .ua("ua")
                        .ip("ip")
                        .ipv6("ipv6")
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(USER_AGENT_HEADER)).isEqualTo("ua");
                    assertThat(headers.getAll(X_FORWARDED_FOR_HEADER)).containsExactly("ip", "ipv6");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bid = givenBid("impId");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId").banner(Banner.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bid = givenBid("impId");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId").video(Video.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bid = givenBid("impId");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId").xNative(Native.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpIdIsMissingInRequest() throws JsonProcessingException {
        // given
        final Bid bid = givenBid("unknown_imp_id");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("known_imp_id"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse(
                        "The impression with ID unknown_imp_id is not present into the request"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(FlatadsBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(givenImpExt("publisherId", "token")))
                .build();
    }

    private static ObjectNode givenImpExt(String publisherId, String token) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpFlatads.of(token, publisherId)));
    }

    private static Bid givenBid(String impId) {
        return Bid.builder().impid(impId).price(BigDecimal.ONE).build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
