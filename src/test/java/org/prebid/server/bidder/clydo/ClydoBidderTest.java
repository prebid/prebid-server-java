package org.prebid.server.bidder.clydo;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.clydo.ExtImpClydo;

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
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ClydoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://region={{Region}}.clydo.io/partnerId={{PartnerId}}";

    private final ClydoBidder target = new ClydoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClydoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(UnaryOperator.identity()),
                        givenImp(imp -> imp.id("321").ext(mapper.valueToTree(ExtPrebid
                                .of(null, ExtImpClydo.of("parentId", "us")))))))
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
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactlyInAnyOrder("123", "321");
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
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("123");

    }

    @Test
    public void makeHttpRequestsShouldIncludeImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("imp1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp1");
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("http://region=us.clydo.io/partnerId=parentId");
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
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("found no valid impressions");

    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

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
    public void makeHttpRequestsShouldUseRegionUsInEndpoint() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("imp-us")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpClydo.of("partner123", "us")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("http://region=us.clydo.io/partnerId=partner123");
    }

    @Test
    public void makeHttpRequestsShouldUseRegionEuInEndpointWithEuRegion() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("imp-us")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpClydo.of("partner123", "eu")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("http://region=eu.clydo.io/partnerId=partner123");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

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
    public void makeHttpRequestsShouldUseDefaultRegionUsWhenRegionIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .id("imp-null-region")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpClydo.of("partner123", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("http://region=us.clydo.io/partnerId=partner123");
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .banner(Banner.builder().w(300).h(250).build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .video(Video.builder().mimes(singletonList("video/mp4")).build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(video);
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .xNative(Native.builder().request("{\"assets\":[]}").build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(xNative);
    }

    @Test
    public void makeBidsShouldReturnAudioBid() throws JsonProcessingException {
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .banner(null)
                .audio(Audio.builder().mimes(singletonList("audio/mp3")).build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(audio);
    }

    @Test
    public void makeBidsShouldReturnErrorForWrongType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("prebid", mapper.createArrayNode());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).id("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badInput("Missing imp id for bid.id: '123'"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenImpHasNoMediaType() throws JsonProcessingException {
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(null).video(null).xNative(null).audio(null));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bidBuilder -> bidBuilder.impid("123")));

        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).first()
                .satisfies(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("Failed to get media type");
                });
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

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpClydo.of("parentId", "us")))))
                .build();
    }

    private static Imp givenBadImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("invalidImp")
                        .ext(mapper.createObjectNode().set("bidder", mapper.createArrayNode())))
                .build();
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
        return mapper.writeValueAsString(bidResponse);
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
