package org.prebid.server.bidder.bidmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidmatic.ExtImpBidmatic;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class BidmaticBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com";

    private BidmaticBidder target;

    @BeforeEach
    public void before() {
        target = new BidmaticBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new BidmaticBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(bidderError -> {
            assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(bidderError.getMessage()).startsWith("Cannot deserialize value");
        });
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerSourceId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").ext(givenImpExt("1")),
                imp -> imp.id("givenImp2").ext(givenImpExt("1")),
                imp -> imp.id("givenImp3").ext(givenImpExt("2")));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getImp().stream().map(Imp::getId).collect(Collectors.toList()))
                .containsExactlyInAnyOrder(List.of("givenImp1", "givenImp2"), List.of("givenImp3"));
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIdsAndCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").ext(givenImpExt("1")),
                imp -> imp.id("givenImp2").ext(givenImpExt("1")),
                imp -> imp.id("givenImp3").ext(givenImpExt("2")));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds, HttpRequest::getUri)
                .containsExactlyInAnyOrder(
                        tuple(Set.of("givenImp1", "givenImp2"), "https://test-url.com?source=1"),
                        tuple(Set.of("givenImp3"), "https://test-url.com?source=2"));
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
    public void makeHttpRequestsShouldReturnImpWithUpdatedBidFloorWhenImpExtHasValidBidFloor() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").bidfloor(BigDecimal.TEN).ext(givenImpExt("1", BigDecimal.ONE)),
                imp -> imp.id("givenImp2").bidfloor(BigDecimal.TEN).ext(givenImpExt("1", BigDecimal.ZERO)));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId, Imp::getBidfloor)
                .containsOnly(tuple("givenImp1", BigDecimal.ONE), tuple("givenImp2", BigDecimal.TEN));
    }

    @Test
    public void makeHttpRequestsShouldReturnImpWithUpdatedExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").ext(givenImpExt("1", new BigDecimal("10.37"))));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedNode = mapper.createObjectNode();
        expectedNode.set("source", IntNode.valueOf(1));
        expectedNode.set("placementId", IntNode.valueOf(2));
        expectedNode.set("siteId", IntNode.valueOf(3));
        expectedNode.set("bidFloor", DecimalNode.valueOf(new BigDecimal("10.37")));
        final ObjectNode expectedImpExt = mapper.createObjectNode().set("bidmatic", expectedNode);

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("impId1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.id("impId2").ext(givenImpExt("string")),
                imp -> imp.id("impId3"));

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("impId3");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSourceIdCanNotBeParsedToInt() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("string")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("Cannot parse sourceId=string to int"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidResponseHasEmptySeatbids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(
                BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(bid -> bid.id("bidId").impid("impId"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.id("impId").banner(Banner.builder().build())),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(bannerBid.toBuilder().mtype(1).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(bid -> bid.id("bidId").impid("impId"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.id("impId").video(Video.builder().build())),
                givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(videoBid.toBuilder().mtype(2).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBid() throws JsonProcessingException {
        // given
        final Bid audioBid = givenBid(bid -> bid.id("bidId").impid("impId"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.id("impId").audio(Audio.builder().build())),
                givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(audioBid.toBuilder().mtype(3).build(), audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(bid -> bid.id("bidId").impid("impId"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.id("impId").xNative(Native.builder().build())),
                givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(nativeBid.toBuilder().mtype(4).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidIsNotInRequest() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(bid -> bid.id("bidId").impid("anotherImpId"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(imp -> imp.id("impId").xNative(Native.builder().build())),
                givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse(
                "ignoring bid id=bidId, request doesn't contain any impression with id=anotherImpId"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(BidmaticBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.TEN)
                        .bidfloorcur("USD")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                null,
                                ExtImpBidmatic.of("100", 1, 2, BigDecimal.TEN)))))
                .build();
    }

    private static ObjectNode givenImpExt(String sourceId) {
        return givenImpExt(sourceId, BigDecimal.TWO);
    }

    private static ObjectNode givenImpExt(String sourceId, BigDecimal bidFloor) {
        return mapper.valueToTree(ExtPrebid.of(
                null,
                ExtImpBidmatic.of(sourceId, 2, 3, bidFloor)));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .cur("USD")
                .build());
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
