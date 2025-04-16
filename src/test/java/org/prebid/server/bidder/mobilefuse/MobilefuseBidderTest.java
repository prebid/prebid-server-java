package org.prebid.server.bidder.mobilefuse;

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
import org.prebid.server.proto.openrtb.ext.request.mobilefuse.ExtImpMobilefuse;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class MobilefuseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/openrtb?pub_id=";

    private final MobilefuseBidder target = new MobilefuseBidder(ENDPOINT_URL, jacksonMapper);

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("imp_id")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, 2)))))
                .build();
    }

    private static String givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {

        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidExtFound() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("456")
                .banner(null)
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid ExtImpMobilefuse value"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MobilefuseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSetPubIdToZeroIfPublisherIdNotPresentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/openrtb?pub_id=0");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImpsFound() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(impBuilder -> impBuilder
                                .id("456")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))),
                        givenImp(impBuilder -> impBuilder.audio(Audio.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No valid imps"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestWithValidImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(impBuilder -> impBuilder.id("imp_id1").banner(Banner.builder().build())),
                        givenImp(impBuilder -> impBuilder.id("imp_id2").video(Video.builder().build())),
                        givenImp(impBuilder -> impBuilder.id("imp_id3").xNative(Native.builder().build())),
                        givenImp(impBuilder -> impBuilder.id("imp_id4").audio(Audio.builder().build())),
                        givenImp(impBuilder -> impBuilder
                                .id("imp_id5")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp_id1", "imp_id2", "imp_id3");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpTagId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().build())
                        .tagid("some tag id")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpMobilefuse.of(1, 2)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt, Imp::getTagid)
                .containsExactly(tuple(null, "1"));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWithAddingSkadnWhenSkadnIsPresent() {
        // given
        final ObjectNode skadn = mapper.createObjectNode().put("something", "something");
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.set("bidder", mapper.valueToTree(ExtImpMobilefuse.of(1, 2)));
        impExt.set("skadn", skadn);
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().build()).ext(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode().set("skadn", skadn);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt, Imp::getTagid)
                .containsExactly(tuple(expectedImpExt, "1"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.impid("imp_id")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenBidExtHasVideoType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("mf", mapper.createObjectNode().put("media_type", "video"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("imp_id")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenBidExtHasNativeType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("mf", mapper.createObjectNode().put("media_type", "native"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("imp_id")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenBidExtHasAnyType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("mf", mapper.createObjectNode().put("media_type", "audio"));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("imp_id")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), banner, "USD"));
    }

}
