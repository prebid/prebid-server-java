package org.prebid.server.bidder.bematterfull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bematterfull.ExtImpBematterfull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class BematterfullBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com?host={{Host}}&pid={{SourceId}}";

    private final BematterfullBidder target = new BematterfullBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BematterfullBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasOnlyInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to deserialize Bematterfull extension");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                }
        );
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasValidAndInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Failed to deserialize Bematterfull extension");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com?host=env&pid=pid");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .allSatisfy(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(bidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getImpIds()).isEqualTo(Set.of("imp_id")));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Failed to decode");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
        });
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
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "banner");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid bannerBid = Bid.builder().impid("3").ext(bidExtNode).build();

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
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "video");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid videoBid = Bid.builder().impid("3").ext(bidExtNode).build();

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
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "native");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid nativeBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, null));
    }

    @Test
    public void shouldReturnErrorWhileMakingBidsWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "unknown");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid invalidBid = Bid.builder().impid("3").ext(bidExtNode).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Failed to parse bid[i].ext.prebid.type");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
        });
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .imp(Arrays.stream(impCustomizers).map(BematterfullBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt())).build();
    }

    private static ObjectNode givenImpExt() {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpBematterfull.of("env", "pid")));
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
