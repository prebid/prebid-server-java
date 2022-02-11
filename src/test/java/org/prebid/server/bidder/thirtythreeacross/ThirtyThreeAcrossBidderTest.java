package org.prebid.server.bidder.thirtythreeacross;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossExtTtx;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossExtTtxCaller;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossImpExt;
import org.prebid.server.bidder.thirtythreeacross.proto.ThirtyThreeAcrossImpExtTtx;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.thirtythreeacross.ExtImpThirtyThreeAcross;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class ThirtyThreeAcrossBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ThirtyThreeAcrossBidder thirtyThreeAcrossBidder;

    @Before
    public void setUp() {
        thirtyThreeAcrossBidder = new ThirtyThreeAcrossBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThirtyThreeAcrossBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldAppendErrorIfExtTtxCouldNotBeParsed() {
        // given
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("ttx", mapper.createArrayNode());
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(ext), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .allSatisfy(error -> assertThat(error).startsWith("Cannot deserialize value of"));
    }

    @Test
    public void makeHttpRequestsShouldAddExtTtxIfNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("ttx", mapper.valueToTree(ThirtyThreeAcrossExtTtx.of(singletonList(
                ThirtyThreeAcrossExtTtxCaller.of("Prebid-Server-Java", "n/a")))));
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(ext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateExtTtx() {
        // given
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("ttx", mapper.valueToTree(ThirtyThreeAcrossExtTtx.of(singletonList(
                ThirtyThreeAcrossExtTtxCaller.of("Caller", "1.0")))));
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(ext), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        final ExtRequest updatedExt = ExtRequest.empty();
        updatedExt.addProperty("ttx", mapper.valueToTree(ThirtyThreeAcrossExtTtx.of(List.of(
                ThirtyThreeAcrossExtTtxCaller.of("Caller", "1.0"),
                ThirtyThreeAcrossExtTtxCaller.of("Prebid-Server-Java", "n/a")))));
        assertThat(result.getValue()).extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(updatedExt);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldAppendErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateSiteIfSiteNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.site(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldNotCreateNewSiteIfSiteNotPresentInBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getSite)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldChangeOnlyFirstImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(
                        ThirtyThreeAcrossImpExt.of(ThirtyThreeAcrossImpExtTtx.of("productId", "zoneId"))));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoParamsNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpThirtyThreeAcross.of("11", null, "3")))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("One or more invalid or missing video field(s) w, h, "
                        + "protocols, mimes, playbackmethod"));
    }

    @Test
    public void makeHttpRequestsShouldUpdateNotPresentPlacement() {
        // given
        final ExtPrebid<?, ExtImpThirtyThreeAcross> ext = ExtPrebid.of(null,
                ExtImpThirtyThreeAcross.of("11", null, "3"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .video(validVideo())
                        .ext(mapper.valueToTree(ext)))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getPlacement)
                .containsExactly(2);
    }

    @Test
    public void makeHttpRequestsShouldNotUpdatePlacementWhenProductIdIsNotInstreamAndPlacementIsNotZero() {
        // given
        final ExtPrebid<?, ExtImpThirtyThreeAcross> ext = ExtPrebid.of(null,
                ExtImpThirtyThreeAcross.of("11", null, "3"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .video(validVideo().toBuilder().placement(23).build())
                        .ext(mapper.valueToTree(ext)))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getPlacement)
                .containsExactly(23);
    }

    @Test
    public void makeHttpRequestsShouldUpdatePlacementAndStartDelayIfProdIsInstream() {
        // given
        final ExtPrebid<?, ExtImpThirtyThreeAcross> ext = ExtPrebid.of(null,
                ExtImpThirtyThreeAcross.of("11", null, "instream"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder
                        .video(validVideo())
                        .ext(mapper.valueToTree(ext)))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getPlacement, Video::getStartdelay)
                .containsExactly(tuple(1, 0));
    }

    @Test
    public void makeBidsShouldReturnErrorIfNoBannerOrVideoPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(givenImp(impBuilder -> impBuilder.banner(null))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = thirtyThreeAcrossBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Imp ID 123 must have at least one of [Banner, Video] defined"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoInBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("ttx",
                mapper.createObjectNode().put("mediaType", "video"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Bid expectedBid = Bid.builder().ext(bidExt).build();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfExtNotContainVideoString() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("ttx",
                mapper.createObjectNode().put("mediaType", "notVideo"));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt))));

        // when
        final Result<List<BidderBid>> result = thirtyThreeAcrossBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Bid expectedBid = Bid.builder().ext(bidExt).build();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, banner, "USD"));
    }

    private static Video validVideo() {
        return Video.builder()
                .w(23)
                .h(23)
                .mimes(singletonList("mime"))
                .protocols(singletonList(23))
                .playbackmethod(singletonList(27))
                .build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        final List<Imp> imps = singletonList(givenImp(impCustomizer));
        return bidRequestCustomizer.apply(BidRequest.builder().imp(imps)).build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final ExtPrebid<?, ExtImpThirtyThreeAcross> ext = ExtPrebid.of(null,
                ExtImpThirtyThreeAcross.of("siteId", "zoneId", "productId"));
        final Imp.ImpBuilder builder = Imp.builder()
                .id("123")
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ext));

        return impCustomizer.apply(builder).build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
