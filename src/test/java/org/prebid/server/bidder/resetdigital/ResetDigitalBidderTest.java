package org.prebid.server.bidder.resetdigital;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ResetDigitalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";
    private ResetDigitalBidder target;

    @BeforeEach
    public void setUp() {
        target = new ResetDigitalBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResetDigitalBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenNoImpressions() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(List.of()).build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("ResetDigital adapter supports only one impression per request");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenMultipleImpressions() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImp(identity()), givenImp(identity())))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("ResetDigital adapter supports only one impression per request");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtIsInvalid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).contains("imp.ext.bidder is required");
                });
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com?pid=placementId123");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdFromPlacementIdWhenEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getPayload().getImp().getFirst().getTagid())
                .isEqualTo("placementId123");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotOverrideExistingTagId() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.tagid("existingTagId"));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);
        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getPayload().getImp().getFirst().getTagid())
                .isEqualTo("existingTagId");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), "invalid");
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
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(null));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(BidResponse.builder().build()));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidByMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(1))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(1)
                        .price(BigDecimal.ONE).build(), BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidByMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(2))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(2)
                        .price(BigDecimal.ONE).build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidByMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(4))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(4)
                        .price(BigDecimal.ONE).build(), BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidByMType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(3))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").mtype(3)
                        .price(BigDecimal.ONE).build(), BidType.audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidFromImpWhenMTypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123")
                        .price(BigDecimal.ONE).build(), BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidFromImpWhenMTypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(null).video(Video.builder().build())),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123")
                        .price(BigDecimal.ONE).build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnAudioBidFromImpWhenMTypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(null).audio(Audio.builder().build())),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123")
                        .price(BigDecimal.ONE).build(), BidType.audio, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidFromImpWhenMTypeIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(null).xNative(Native.builder().build())),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123")
                        .price(BigDecimal.ONE).build(), BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldFilterOutZeroPriceBids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ZERO))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("price 0 <= 0 filtered out");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMTypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(99))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unsupported MType: 99");
    }

    @Test
    public void makeBidsShouldUseCurrencyFromBidResponse() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                jacksonMapper.mapper().writeValueAsString(
                        givenBidResponse(identity()).toBuilder().cur("EUR").build()));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);
        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("EUR");
    }

    private BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer)))
                .build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(300).h(250).build())
                        .ext(jacksonMapper.mapper().createObjectNode()
                                .set("bidder", jacksonMapper.mapper().createObjectNode()
                                        .put("placement_id", "placementId123"))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()
                                .impid("123")
                                .price(BigDecimal.ONE)).build()))
                        .build()))
                .cur("USD")
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
