package org.prebid.server.bidder.teqblaze;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.teqblaze.proto.TeqblazeExtImp;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.teqblaze.ExtImpTeqblaze;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class TeqblazeBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";

    private final TeqblazeBidder target = new TeqblazeBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TeqblazeBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(UnaryOperator.identity()), givenImp(UnaryOperator.identity())))
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(1);
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherIsNot() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(UnaryOperator.identity()), givenBadImp(UnaryOperator.identity())))
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnExtTypePublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(givenImpExtTeqblazeBidder(ext -> ext
                        .type("publisher")
                        .placementId("somePlacementId"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(givenImpExtTeqblazeBidder(ext -> ext
                        .type("publisher")
                        .placementId("somePlacementId")));
    }

    @Test
    public void makeHttpRequestsShouldReturnExtTypeNetwork() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(givenImpExtTeqblazeBidder(ext -> ext
                        .type("publisher")
                        .endpointId("someEndpointId"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(givenImpExtTeqblazeBidder(ext -> ext.type("network").endpointId("someEndpointId")));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.createObjectNode().set("bidder", mapper.createArrayNode())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();

        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .anySatisfy(msg -> assertThat(msg).startsWith("Cannot deserialize ExtImpTeqblaze:"));
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
    public void makeHttpRequestsShouldCreateMultipleRequestsForMultipleImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(UnaryOperator.identity()),
                        givenImp(UnaryOperator.identity()),
                        givenImp(UnaryOperator.identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3);
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
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
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(bid -> bid.id("bidId").impid("impId").mtype(1));
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
        final Bid videoBid = givenBid(bid -> bid.id("bidId").impid("impId").mtype(2));
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
        final Bid audioBid = givenBid(bid -> bid.id("bidId").impid("impId").mtype(3));
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
        final Bid nativeBid = givenBid(bid -> bid.id("bidId").impid("impId").mtype(4));
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
    public void makeBidsShouldReturnErrorForUnsupportedMType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final Bid bid = givenBid(bidBuilder -> bidBuilder
                .ext(bidExt)
                .impid("123")
                .mtype(5));

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                givenBidResponse(bid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("could not define media type for impression: 123");
        assertThat(result.getValue()).isEmpty();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTeqblaze.of("placementId", "endpointId")))))
                .build();
    }

    private static Imp givenBadImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("invalidImp")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpTeqblaze.of(null, null)))))
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(UnaryOperator.identity(), impCustomizer);
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(asList(bids)).build()))
                .cur("USD")
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private ObjectNode givenImpExtTeqblazeBidder(
            UnaryOperator<TeqblazeExtImp.TeqblazeExtImpBuilder> impExtTeqblazeBidderBuilder) {
        final ObjectNode modifiedImpExtBidder = mapper.createObjectNode();

        return modifiedImpExtBidder.set("bidder", mapper.convertValue(
                impExtTeqblazeBidderBuilder.apply(TeqblazeExtImp.builder())
                        .build(),
                JsonNode.class));
    }
}
