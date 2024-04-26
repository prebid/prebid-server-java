package org.prebid.server.bidder.sovrnxsp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.sovrnxsp.ExtImpSovrnXsp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class SovrnXspBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com";

    private final SovrnXspBidder target = new SovrnXspBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SovrnXspBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestDoesNotHaveApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder().app(null).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getMessage()).startsWith("bidrequest.app must be present");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                }
        );
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasOnlyInvalidImpressions() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().build()).ext(invalidExt),
                impBuilder -> impBuilder.video(null).banner(null).xNative(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(3).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                },
                error -> {
                    assertThat(error.getMessage()).isEqualTo("Banner, video or native should be present");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                },
                error -> {
                    assertThat(error.getMessage()).isEqualTo("No matching impression with ad format");
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
        assertThat(result.getErrors()).hasSize(1).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                }
        );
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri())
                        .isEqualTo(ENDPOINT_URL));
        assertThat(result.getErrors()).isEmpty();
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
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(X_OPENRTB_VERSION_HEADER))
                        .isEqualTo("2.5"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldAddPublisherWithImpExtPubIdWhenPublisherIsMissing() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pubId("imp_ext_pub_id"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt)).toBuilder()
                .app(App.builder().id("app_id").publisher(null).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final Publisher expectedPublisher = Publisher.builder().id("imp_ext_pub_id").build();
        final BidRequest expectedBidRequest = bidRequest.toBuilder()
                .app(bidRequest.getApp().toBuilder().publisher(expectedPublisher).build())
                .build();
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAppPublisherWithImpExtPubIdWhenPublisherIsPresentAndHasId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pubId("imp_ext_pub_id"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt)).toBuilder()
                .app(App.builder().id("app_id").publisher(Publisher.builder().id("pub_id").build()).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final Publisher expectedPublisher = bidRequest.getApp().getPublisher().toBuilder().id("imp_ext_pub_id").build();
        final BidRequest expectedBidRequest = bidRequest.toBuilder()
                .app(bidRequest.getApp().toBuilder().publisher(expectedPublisher).build())
                .build();
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnAppWithImpExtMedId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.medId("imp_ext_med_id"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt)).toBuilder()
                .app(App.builder().id("app_id").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final Publisher expectedPublisher = Publisher.builder().build();
        final BidRequest expectedBidRequest = bidRequest.toBuilder()
                .app(bidRequest.getApp().toBuilder().id("imp_ext_med_id").publisher(expectedPublisher).build())
                .build();
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithTagIdAsImpExtZoneId() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.zoneId("imp_ext_zone_id"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.tagid("tagid").ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final Imp expectedImp = bidRequest.getImp().get(0).toBuilder().tagid("imp_ext_zone_id").build();
        final BidRequest expectedBidRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(expectedImp))
                .build();
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnImpExtForceBidAsIs() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.forceBid(true));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

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
        assertThat(results.getValue()).hasSize(1).flatExtracting(HttpRequest::getImpIds).containsOnly("imp_id");
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
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final ObjectNode bidExtNode = mapper.createObjectNode().put("creative_type", 0);
        final Bid bannerBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = bannerBid.toBuilder().mtype(1).build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        // given
        final ObjectNode bidExtNode = mapper.createObjectNode().put("creative_type", 1);
        final Bid videoBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = videoBid.toBuilder().mtype(2).build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, video, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final ObjectNode bidExtNode = mapper.createObjectNode().put("creative_type", 2);
        final Bid nativeBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = nativeBid.toBuilder().mtype(4).build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, xNative, null));
    }

    @Test
    public void shouldReturnErrorWhileMakingBidsWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final ObjectNode bidExtNode = mapper.createObjectNode().put("creative_type", 4);
        final Bid invalidBid = Bid.builder().impid("3").ext(bidExtNode).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(invalidBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getMessage()).startsWith("Unsupported creative type: 4");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                }
        );
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .app(App.builder().id("app_id").publisher(Publisher.builder().id("publisher_id").build()).build())
                .imp(Arrays.stream(impCustomizers).map(SovrnXspBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("imp_id")
                .banner(Banner.builder().build())
                .ext(givenImpExt(identity())))
                .build();
    }

    private static ObjectNode givenImpExt(UnaryOperator<ExtImpSovrnXsp.ExtImpSovrnXspBuilder> impExtCustomizer) {
        return mapper.valueToTree(ExtPrebid.of(null, impExtCustomizer.apply(ExtImpSovrnXsp.builder()).build()));
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
