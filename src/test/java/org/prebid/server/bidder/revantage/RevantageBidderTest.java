package org.prebid.server.bidder.revantage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.revantage.ExtImpRevantage;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class RevantageBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://bid.revantage.io/bid";

    private RevantageBidder target;

    @BeforeEach
    public void setUp() {
        target = new RevantageBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpoint() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RevantageBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenFeedIdMissing() {
        // given
        final BidRequest request = givenBidRequest(impBuilder ->
                impBuilder.id("imp-1").ext(givenImpExt(null, null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
        assertThat(result.getErrors().get(0).getMessage()).contains("feedId");
    }

    @Test
    public void makeHttpRequestsShouldEmitOneRequestPerFeedId() {
        // given
        final Imp imp1 = givenImp(b -> b.id("imp-1").ext(givenImpExt("feed-one", null, null)));
        final Imp imp2 = givenImp(b -> b.id("imp-2").ext(givenImpExt("feed-two", null, null)));
        final Imp imp3 = givenImp(b -> b.id("imp-3").ext(givenImpExt("feed-one", null, null)));
        final BidRequest request = BidRequest.builder().imp(List.of(imp1, imp2, imp3)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);

        // First feed seen → first request
        assertThat(result.getValue().get(0).getUri()).isEqualTo(ENDPOINT_URL + "?feed=feed-one");
        assertThat(result.getValue().get(0).getPayload().getImp())
                .extracting(Imp::getId).containsExactly("imp-1", "imp-3");

        assertThat(result.getValue().get(1).getUri()).isEqualTo(ENDPOINT_URL + "?feed=feed-two");
        assertThat(result.getValue().get(1).getPayload().getImp())
                .extracting(Imp::getId).containsExactly("imp-2");
    }

    @Test
    public void makeHttpRequestsShouldRewriteImpExtToEndpointShape() {
        // given
        final BidRequest request = givenBidRequest(b ->
                b.id("imp-1").ext(givenImpExt("feed-abc", "plc-1", "pub-1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final HttpRequest<BidRequest> http = result.getValue().get(0);
        assertThat(http.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(http.getUri()).isEqualTo(ENDPOINT_URL + "?feed=feed-abc");

        final ObjectNode rewrittenExt = (ObjectNode) http.getPayload().getImp().get(0).getExt();
        assertThat(rewrittenExt.get("feedId").asText()).isEqualTo("feed-abc");
        assertThat(rewrittenExt.get("bidder").get("placementId").asText()).isEqualTo("plc-1");
        assertThat(rewrittenExt.get("bidder").get("publisherId").asText()).isEqualTo("pub-1");
    }

    @Test
    public void makeHttpRequestsShouldUrlEncodeFeedIdInQueryParam() {
        // given
        final BidRequest request = givenBidRequest(b ->
                b.id("imp-1").ext(givenImpExt("feed with spaces & symbols", null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(request);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
                .isEqualTo(ENDPOINT_URL + "?feed=feed+with+spaces+%26+symbols");
    }

    @Test
    public void makeBidsShouldReturnEmptyListOn204() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(204, null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIs1() throws Exception {
        // given
        final BidResponse response = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .seat("dsp-x")
                        .bid(singletonList(Bid.builder()
                                .id("b1").impid("imp-1").price(java.math.BigDecimal.valueOf(1.25))
                                .adm("<div>Ad</div>").crid("c1").w(300).h(250).mtype(1)
                                .build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(200, mapper.writeValueAsString(response));

        final BidRequest request = givenBidRequest(b ->
                b.id("imp-1").ext(givenImpExt("feed-abc", null, null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType, BidderBid::getSeat, BidderBid::getBidCurrency)
                .containsExactly(tuple(BidType.banner, "dsp-x", "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMtypeIs2() throws Exception {
        // given
        final BidResponse response = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .seat("video-dsp")
                        .bid(singletonList(Bid.builder()
                                .id("v1").impid("imp-v").price(java.math.BigDecimal.valueOf(3.50))
                                .adm("<VAST version=\"3.0\"></VAST>").crid("vc1").w(640).h(480).mtype(2)
                                .build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(200, mapper.writeValueAsString(response));

        final BidRequest request = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("imp-v")
                        .video(Video.builder().w(640).h(480).build())
                        .ext(givenImpExt("feed-v", null, null))
                        .build()))
                .build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldDetectVideoFromVastWhenMtypeAbsent() throws Exception {
        // given
        final BidResponse response = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .seat("dsp")
                        .bid(singletonList(Bid.builder()
                                .id("v1").impid("imp-multi").price(java.math.BigDecimal.valueOf(2.0))
                                .adm("<VAST version=\"3.0\"></VAST>").crid("c").w(640).h(480)
                                .build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(200, mapper.writeValueAsString(response));

        // multi-format imp so neither imp-shape detection alone is enough
        final BidRequest request = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("imp-multi")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().w(640).h(480).build())
                        .ext(givenImpExt("feed-m", null, null))
                        .build()))
                .build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldFallBackToBannerForBannerOnlyImp() throws Exception {
        // given — bid with no mtype, no ext.mediaType, non-VAST adm, imp is banner-only
        final BidResponse response = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .seat("dsp")
                        .bid(singletonList(Bid.builder()
                                .id("b").impid("imp-1").price(java.math.BigDecimal.valueOf(0.5))
                                .adm("<div>plain html</div>").crid("c").w(300).h(250)
                                .build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(200, mapper.writeValueAsString(response));

        final BidRequest request = givenBidRequest(b ->
                b.id("imp-1").ext(givenImpExt("feed-abc", null, null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, request);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnErrorOnMalformedJson() throws Exception {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(200, "not-json");

        // when
        final Result<List<BidderBid>> result =
                target.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
    }

    @Test
    public void makeBidsShouldDefaultCurrencyToUsd() throws Exception {
        // given — response with no `cur`
        final BidResponse response = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .seat("dsp")
                        .bid(singletonList(Bid.builder()
                                .id("b").impid("imp-1").price(java.math.BigDecimal.valueOf(1.0))
                                .adm("<div>x</div>").mtype(1).build()))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(200, mapper.writeValueAsString(response));

        final BidRequest request = givenBidRequest(b ->
                b.id("imp-1").ext(givenImpExt("feed-abc", null, null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, request);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBidCurrency()).isEqualTo("USD");
    }

    // ------------ helpers ------------

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> customizer) {
        return BidRequest.builder().imp(singletonList(givenImp(customizer))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> customizer) {
        return customizer.apply(Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .w(300).h(250).build()))
                .build();
    }

    private static ObjectNode givenImpExt(String feedId, String placementId, String publisherId) {
        return mapper.valueToTree(
                ExtPrebid.of(null, ExtImpRevantage.of(feedId, placementId, publisherId)));
    }

    private static BidderCall<BidRequest> givenHttpCall(int statusCode, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(BidRequest.builder().build()).build(),
                HttpResponse.of(statusCode, null, body),
                null);
    }
}
