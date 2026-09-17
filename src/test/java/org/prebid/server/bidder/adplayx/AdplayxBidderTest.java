package org.prebid.server.bidder.adplayx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adplayx.ExtImpAdplayx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AdplayxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/v1.0/ortb";

    private AdplayxBidder target;

    @BeforeEach
    public void setUp() {
        target = new AdplayxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpoint() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdplayxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenApptokenIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdplayx.of(null, "placement123")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("apptoken is required"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectUriWithApptokenAndPlacementId() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdplayx.of("test_token", "placement123")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
                .isEqualTo("https://test.endpoint.com/v1.0/ortb?apptoken=test_token&placementid=placement123");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectUriWithoutPlacementIdWhenMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdplayx.of("test_token", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
                .isEqualTo("https://test.endpoint.com/v1.0/ortb?apptoken=test_token");
    }

    @Test
    public void makeHttpRequestsShouldUrlEncodeSpecialCharactersWithoutDoubleEncoding() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdplayx.of("token & 123", "placement 456")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
                .isEqualTo("https://test.endpoint.com/v1.0/ortb?apptoken=token%20%26%20123&placementid=placement%20456");
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp_id_1").banner(Banner.builder().build()));

        final BidResponse bidResponse = givenBidResponse(bid -> bid
                .impid("imp_id_1")
                .price(BigDecimal.valueOf(1.5)));

        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final BidderBid expectedBid = BidderBid.of(
                Bid.builder().impid("imp_id_1").price(BigDecimal.valueOf(1.5)).build(),
                BidType.banner,
                "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBid);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp_id_video").video(Video.builder().build()));

        final BidResponse bidResponse = givenBidResponse(bid -> bid
                .impid("imp_id_video")
                .price(BigDecimal.valueOf(2.0)));

        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        final BidderBid expectedBid = BidderBid.of(
                Bid.builder().impid("imp_id_video").price(BigDecimal.valueOf(2.0)).build(),
                BidType.video,
                "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(expectedBid);
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder().id("123")).build()))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
