package org.prebid.server.bidder.amt;

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
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.amt.ExtImpAmt;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class AmtBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final AmtBidder target = new AmtBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AmtBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
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
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(10)))
                )
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(10)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(), video, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(10)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(), xNative, null));
    }

    @Test
    public void makeBidsShouldReturnAudioBidIfAudioIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.audio(Audio.builder().build()));
        final String bidResponse = mapper.writeValueAsString(
                givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(10))));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(), audio, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerAndVideoAndAudioAndNativeIsAbsentInRequestImp()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(
                        givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(10)))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(), banner, null));
    }

    @Test
    public void makeHttpRequestsShouldSetPriceFloorAndPriceCeilingInExtToNullIfNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), null, null);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> httpRequest.getPayload().getImp().get(0).getBidfloor())
                .containsNull();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> httpRequest.getPayload().getImp().get(0).getExt().get("bidFloor"))
                .containsOnlyNulls();

        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> httpRequest.getPayload().getImp().get(0).getExt().get("bidCeiling"))
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldRemoveBidsIfPriceIsNotInBidFloorAndBidCeilingRange() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.audio(Audio.builder().build()));
        final String bidResponse = mapper.writeValueAsString(
                givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(200000))));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsIfBidFloorNotPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), null, BigDecimal.valueOf(10000));
        final String bidResponse = mapper.writeValueAsString(
                givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(100))));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .contains(Bid.builder().impid("123").price(BigDecimal.valueOf(100)).build());
    }

    @Test
    public void makeBidsShouldReturnBidsIfBidCeilingNotPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), BigDecimal.ONE, null);
        final String bidResponse = mapper.writeValueAsString(
                givenBidResponse(impBuilder -> impBuilder.impid("123").price(BigDecimal.valueOf(100))));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .contains(Bid.builder().impid("123").price(BigDecimal.valueOf(100)).build());
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        final ExtImpAmt extBuilder = ExtImpAmt.of("123", BigDecimal.ZERO, BigDecimal.valueOf(100000));
        final ObjectNode impExt = jacksonMapper.mapper().valueToTree(ExtPrebid.of(null, extBuilder));
        final Imp imp = impCustomizer.apply(Imp.builder().id("123").bidfloor(BigDecimal.ZERO).ext(impExt)).build();

        return bidRequestCustomizer.apply(BidRequest.builder().imp(singletonList(imp))).build();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer, BigDecimal bidFloor, BigDecimal bidCeiling) {
        return givenBidRequest(identity(), impCustomizer, bidFloor, bidCeiling);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer, BigDecimal bidFloor, BigDecimal bidCeiling) {

        final ExtImpAmt extBuilder = ExtImpAmt.of("123", bidFloor, bidCeiling);
        final ObjectNode impExt = jacksonMapper.mapper().valueToTree(ExtPrebid.of(null, extBuilder));
        final Imp imp = impCustomizer.apply(Imp.builder().id("123").bidfloor(bidFloor).ext(impExt)).build();

        return bidRequestCustomizer.apply(BidRequest.builder().imp(singletonList(imp))).build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static Bid givenBid() {
        return Bid.builder().impid("123").price(BigDecimal.valueOf(10)).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
