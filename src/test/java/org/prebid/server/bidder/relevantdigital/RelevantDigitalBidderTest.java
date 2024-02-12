package org.prebid.server.bidder.relevantdigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.screencore.ScreencoreBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.relevantdigital.ExtImpRelevantDigital;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class RelevantDigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://{{Host}}/pbs";

    private final RelevantDigitalBidder target = new RelevantDigitalBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ScreencoreBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasOnlyInvalidImpressions() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).first().satisfies(
                error -> assertThat(error.getMessage()).startsWith("Cannot deserialize"),
                error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input));
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
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final ObjectNode impExt = givenImpExt(
                extBuilder -> extBuilder.pbsHost("https://coolcustomhost.relevant-digital.com"));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getUri())
                        .isEqualTo("http://coolcustomhost/pbs"));
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
                        .isEqualTo("2.5"))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER))
                        .isEqualTo("ua"))
                .satisfies(headers -> assertThat(headers.getAll(X_FORWARDED_FOR_HEADER))
                        .isEqualTo(List.of("ipv6", "ip")));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithStoredRequestIdAndWithoutRelevantDigitalInfo() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.placementId("placementId"));
        final ObjectNode relevantExtNode = mapper.createObjectNode().put("field", "value");
        final ObjectNode relevantExt = mapper.createObjectNode().set("relevant", relevantExtNode);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(impExt)
                .banner(Banner.builder().ext(relevantExt).build())
                .video(Video.builder().ext(relevantExt).build())
                .audio(Audio.builder().ext(relevantExt).build())
                .xNative(Native.builder().ext(relevantExt).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ExtImpPrebid expectedExtImpPrebid = ExtImpPrebid.builder()
                .storedrequest(ExtStoredRequest.of("placementId"))
                .build();
        final ObjectNode expectedExtImp = mapper.valueToTree(ExtPrebid.of(expectedExtImpPrebid, null));
        final BidRequest expectedBidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder().ext(mapper.createObjectNode()).build())
                .video(Video.builder().ext(mapper.createObjectNode()).build())
                .audio(Audio.builder().ext(mapper.createObjectNode()).build())
                .xNative(Native.builder().ext(mapper.createObjectNode()).build())
                .ext(expectedExtImp));
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getImp()).isEqualTo(expectedBidRequest.getImp()));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnOriginalTMaxWhenTMaxIsHigherThanZeroAndBufferTMaxIsAbsent() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(100L)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(100));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnDefaultTMaxWhenTMaxIsAbsentAndBufferTMaxIsAbsent() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(null));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(null)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(1000));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnOriginalTMaxWhenBufferIsHigherThanOriginalTimeout() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(2000L));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(150L)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(150));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnDefaultTMaxWhenBufferIsHigherThanDefaultTimeout() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(2000L));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(null)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(1000));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnBufferTmaxWhenBufferIsLowerNotMoreThanTwiceThanOriginalTimeout() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(700L));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(1000L)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(700));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnTimeourAndBufferDiffTmaxWhenBufferIsAtLeastTwiceLowerThanOriginalTimeout() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.pbsBufferMs(300L));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .tmax(1000L)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getTmax()).isEqualTo(700));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestExtWithStoredRequestIdAndRelevantInfoWhenRequestIsFirstOne() {
        // given
        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.accountId("accountId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ExtRequestPrebid expectedExtRequestPrebid = ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("accountId"))
                .build();
        final ExtRequest expectedExtRequest = ExtRequest.of(expectedExtRequestPrebid);
        final ObjectNode expectedRelevantNode = mapper.createObjectNode().put("adapterType", "server").put("count", 1);
        expectedExtRequest.addProperty("relevant", expectedRelevantNode);

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getExt()).isEqualTo(expectedExtRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestExtWithStoredRequestIdAndRelevantInfoWhenRequestIsRecurringOne() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        final ObjectNode relevantNode = mapper.createObjectNode().put("count", 3);
        extRequest.addProperty("relevant", relevantNode);

        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.accountId("accountId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ExtRequestPrebid expectedExtRequestPrebid = ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("accountId"))
                .build();
        final ExtRequest expectedExtRequest = ExtRequest.of(expectedExtRequestPrebid);
        final ObjectNode expectedRelevantNode = mapper.createObjectNode().put("adapterType", "server").put("count", 4);
        expectedExtRequest.addProperty("relevant", expectedRelevantNode);

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getExt()).isEqualTo(expectedExtRequest));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailWhenRequestIsFifthRecurringOne() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        final ObjectNode relevantNode = mapper.createObjectNode().put("count", 5);
        extRequest.addProperty("relevant", relevantNode);

        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.accountId("accountId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).isEmpty();
        assertThat(results.getErrors()).hasSize(1).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getMessage()).isEqualTo("too many requests");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestExtPrebidWithCleanedUpAliasesTargetingAndCache() {
        // given
        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().build())
                .cache(ExtRequestPrebidCache.EMPTY)
                .aliases(Map.of("alias1", "value1", "alias2", "value2"))
                .build());

        final ObjectNode impExt = givenImpExt(extBuilder -> extBuilder.accountId("accountId"));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(impExt))
                .toBuilder()
                .ext(extRequest)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final ExtRequestPrebid expectedExtRequestPrebid = ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("accountId"))
                .targeting(null)
                .aliases(null)
                .cache(null)
                .build();
        final ExtRequest expectedExtRequest = ExtRequest.of(expectedExtRequestPrebid);
        final ObjectNode expectedRelevantNode = mapper.createObjectNode()
                .put("adapterType", "server")
                .put("count", 1);
        expectedExtRequest.addProperty("relevant", expectedRelevantNode);

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload().getExt()).isEqualTo(expectedExtRequest));
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
    public void makeBidsShouldReturnBannerBidBasedOnMType() throws JsonProcessingException {
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
    public void makeBidsShouldReturnVideoBidBasedOnMType() throws JsonProcessingException {
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
    public void makeBidsShouldReturnAudioBidBasedOnMType() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().impid("3").mtype(3).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(audioBid, audio, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidBasedOnMType() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("3").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidBasedOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "banner");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid bannerBid = Bid.builder().impid("1").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, null));

    }

    @Test
    public void makeBidsShouldReturnVideoBidBasedOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "video");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid videoBid = Bid.builder().impid("2").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, video, null));
    }

    @Test
    public void makeBidsShouldReturnAudioBidBasedOnBidExt() throws JsonProcessingException {
        // given
        final ObjectNode prebidNode = mapper.createObjectNode().put("type", "audio");
        final ObjectNode bidExtNode = mapper.createObjectNode().set("prebid", prebidNode);
        final Bid audioBid = Bid.builder().impid("3").ext(bidExtNode).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(audioBid, audio, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidOnBidExt() throws JsonProcessingException {
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
        final Bid audioBid = Bid.builder().impid("3").mtype(5).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(badInput("Failed to parse bid[i].ext.prebid.type for the bid 3"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .imp(Arrays.stream(impCustomizers).map(RelevantDigitalBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt(identity()))).build();
    }

    private static ObjectNode givenImpExt(
            UnaryOperator<ExtImpRelevantDigital.ExtImpRelevantDigitalBuilder> impExtCustomizer) {

        return mapper.valueToTree(
                ExtPrebid.of(null, impExtCustomizer.apply(ExtImpRelevantDigital.builder().pbsHost("host")).build()));
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
