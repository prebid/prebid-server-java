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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

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
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnExtTypePublisher() {
        // given
        final BidRequest bidRequest = givenBidRequest(impCustomizer -> impCustomizer
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpTeqblaze.of("somePlacementId", "")))));

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
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpTeqblaze.of("", "someEndpointId")))));

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

        final List<BidderError> errors = result.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("found no valid impressions");
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
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(1))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(video).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(2))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnAudioBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(audio).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").audio(Audio.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(3))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(audio);
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(xNative).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(4))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(xNative);
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(5))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("could not define media type for impression: 123");
        assertThat(result.getValue()).isEmpty();
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

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
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
