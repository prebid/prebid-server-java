package org.prebid.server.bidder.pgamssp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pgamssp.PgamSspImpExt;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class PgamSspBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test-url.com";

    private final PgamSspBidder target = new PgamSspBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PgamSspBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first().isEqualTo(badInput("Invalid ext. Imp.Id: imp_id"));
    }

    @Test
    public void makeHttpRequestsShouldReturnPublisherTypeWhenRequestHasPlacementId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.placementId("placementId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").type("publisher"));
        final BidRequest expectedBidRequest = givenBidRequest(impBuilder -> impBuilder.ext(expectedImpExt));
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnNetworkTypeWhenRequestHasEndpointId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.endpointId("endpointId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = givenImpExt(
                extBuilder -> extBuilder.endpointId("endpointId").type("network"));
        final BidRequest expectedBidRequest = givenBidRequest(impBuilder -> impBuilder.ext(expectedImpExt));
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnPublisherTypeWhenRequestHasPlacementIdAndEndpointId() {
        // given
        final ObjectNode impExt = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").endpointId("endpointId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").type("publisher"));
        final BidRequest expectedBidRequest = givenBidRequest(impBuilder -> impBuilder.ext(expectedImpExt));
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotReturnTypeWhenRequestDoesNotHaveNeitherPlacementIdNorEndpointId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.placementId(null).endpointId(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = givenImpExt(UnaryOperator.identity());
        final BidRequest expectedBidRequest = givenBidRequest(impBuilder -> impBuilder.ext(expectedImpExt));
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturn4RequestsWhenRequestHas4ValidImps() {
        // given
        final ObjectNode impExt1 = givenImpExt(
                extBuilder -> extBuilder.placementId(null).endpointId(null));
        final ObjectNode impExt2 = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").endpointId("endpointId"));
        final ObjectNode impExt3 = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").endpointId(null));
        final ObjectNode impExt4 = givenImpExt(
                extBuilder -> extBuilder.placementId(null).endpointId("endpointId"));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("impid1").ext(impExt1),
                impBuilder -> impBuilder.id("impid2").ext(impExt2),
                impBuilder -> impBuilder.id("impid3").ext(impExt3),
                impBuilder -> impBuilder.id("impid4").ext(impExt4));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt1 = givenImpExt(
                extBuilder -> extBuilder.placementId(null).endpointId(null).type(null));
        final ObjectNode expectedImpExt2 = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").type("publisher"));
        final ObjectNode expectedImpExt3 = givenImpExt(
                extBuilder -> extBuilder.placementId("placementId").type("publisher"));
        final ObjectNode expectedImpExt4 = givenImpExt(
                extBuilder -> extBuilder.endpointId("endpointId").type("network"));
        final BidRequest expectedBidRequest1 = givenBidRequest(
                impBuilder -> impBuilder.id("impid1").ext(expectedImpExt1));
        final BidRequest expectedBidRequest2 = givenBidRequest(
                impBuilder -> impBuilder.id("impid2").ext(expectedImpExt2));
        final BidRequest expectedBidRequest3 = givenBidRequest(
                impBuilder -> impBuilder.id("impid3").ext(expectedImpExt3));
        final BidRequest expectedBidRequest4 = givenBidRequest(
                impBuilder -> impBuilder.id("impid4").ext(expectedImpExt4));
        assertThat(results.getValue()).hasSize(4)
                .extracting(HttpRequest::getPayload, HttpRequest::getBody, HttpRequest::getImpIds)
                .containsExactly(
                        tuple(expectedBidRequest1, jacksonMapper.encodeToBytes(expectedBidRequest1), Set.of("impid1")),
                        tuple(expectedBidRequest2, jacksonMapper.encodeToBytes(expectedBidRequest2), Set.of("impid2")),
                        tuple(expectedBidRequest3, jacksonMapper.encodeToBytes(expectedBidRequest3), Set.of("impid3")),
                        tuple(expectedBidRequest4, jacksonMapper.encodeToBytes(expectedBidRequest4), Set.of("impid4")));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedMethodAndUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getMethod, HttpRequest::getUri)
                .containsExactly(HttpMethod.POST, "http://test-url.com");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("Bad Server Response"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseDoesNotHaveSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, givenBidResponse());

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnAllThreeBidsTypesSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();
        final Bid nativeBid = Bid.builder().impid("3").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(bannerBid, videoBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(bannerBid, banner, null),
                BidderBid.of(videoBid, video, null),
                BidderBid.of(nativeBid, xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, null));

    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, video, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("3").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, null));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(PgamSspBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(
                        Imp.builder()
                                .id("imp_id")
                                .ext(givenImpExt(extImpBuilder -> extImpBuilder.placementId("placementId"))))
                .build();
    }

    private static ObjectNode givenImpExt(UnaryOperator<PgamSspImpExt.PgamSspImpExtBuilder> impExtBuilder) {
        return mapper.valueToTree(ExtPrebid.of(null, impExtBuilder.apply(PgamSspImpExt.builder()).build()));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(bids.length == 0 ? List.of() : List.of(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
