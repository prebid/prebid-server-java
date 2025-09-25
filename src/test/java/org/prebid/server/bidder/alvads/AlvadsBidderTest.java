package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.alvads.model.AlvaAdsImp;
import org.prebid.server.bidder.alvads.model.AlvadsRequestOrtb;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AlvadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://helios-ads-qa-core.ssidevops.com/decision/openrtb";

    private final AlvadsBidder target = new AlvadsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlvadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    void makeHttpRequestsShouldReturnErrorForInvalidImpExt() {
        // given
        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.put("bidder", "invalid");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id("1").ext(extNode).build()))
                .build();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage())
                .contains("Missing or invalid bidder ext");
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequestsUrl() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        result.getValue().forEach(req -> assertThat(req.getUri()).isEqualTo(ENDPOINT_URL));
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequestsHeaders() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        result.getValue().forEach(req -> assertThat(req.getHeaders()).isNotEmpty());
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequestsImpIds() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getImpIds()).contains("imp-1");
        assertThat(result.getValue().get(1).getImpIds()).contains("imp-2");
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequestsImpContent() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HttpRequest<AlvadsRequestOrtb> req1 = result.getValue().get(0);
        final HttpRequest<AlvadsRequestOrtb> req2 = result.getValue().get(1);

        assertThat(req1.getPayload().getImp().get(0).getBanner()).isNotNull();
        assertThat(req1.getPayload().getImp().get(0).getVideo()).isNull();

        assertThat(req2.getPayload().getImp().get(0).getVideo()).isNotNull();
        assertThat(req2.getPayload().getImp().get(0).getBanner()).isNull();
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequestsSiteAndOtherFields() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getPayload().getId()).isEqualTo("req-123");
        assertThat(result.getValue().get(1).getPayload().getId()).isEqualTo("req-123");

        assertThat(result.getValue().get(0).getPayload().getSite()).isNotNull();
        assertThat(result.getValue().get(1).getPayload().getSite()).isNotNull();
    }

    private static BidRequest createBidRequestWithBannerAndVideo() {
        final ObjectNode bidderNode1 = new ObjectMapper().createObjectNode();
        bidderNode1.put("publisherUniqueId", "pub-1");
        bidderNode1.put("endpointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode1 = new ObjectMapper().createObjectNode();
        impExtNode1.set("bidder", bidderNode1);

        final ObjectNode bidderNode2 = new ObjectMapper().createObjectNode();
        bidderNode2.put("publisherUniqueId", "pub-2");
        bidderNode2.put("endpointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode2 = new ObjectMapper().createObjectNode();
        impExtNode2.set("bidder", bidderNode2);

        final Imp imp1 = Imp.builder()
                .id("imp-1")
                .banner(Banner.builder().w(300).h(250).build())
                .ext(impExtNode1)
                .build();

        final Imp imp2 = Imp.builder()
                .id("imp-2")
                .video(com.iab.openrtb.request.Video.builder().w(640).h(480).build())
                .ext(impExtNode2)
                .build();

        final Site site = Site.builder().page("https://example.com").build();

        return BidRequest.builder()
                .id("req-123")
                .imp(List.of(imp1, imp2))
                .site(site)
                .device(Device.builder().build())
                .build();
    }

    @Test
    void makeBidsShouldReturnEmptyListForEmptyResponse() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();
        final HttpResponse response = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final BidderCall<AlvadsRequestOrtb> call = BidderCall.succeededHttp(
                HttpRequest.<AlvadsRequestOrtb>builder().payload(null).build(),
                response,
                null
        );

        // when
        final Result<List<BidderBid>> result = target.makeBids(call, BidRequest.builder().build());

        // then
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    void makeBidsShouldReturnBidderBidsWithFullFields() {
        // given
        final Imp bannerImp = createImp("imp-banner", "pub-1", 300, 250);
        final Imp videoImp = createImp("imp-video", "pub-2", 640, 480);

        final BidRequest bidRequest = createBidRequest(List.of(bannerImp, videoImp));

        final Bid bannerBid = createBid("bid-banner", "imp-banner", 1.5);
        final Bid videoBid = createBid("bid-video", "imp-video", 2.5);

        final SeatBid seatBid = createSeatBid(bannerBid, videoBid);
        final BidResponse bidResponse = createBidResponse(List.of(seatBid), "USD");

        final HttpResponse httpResponse = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final HttpRequest<AlvadsRequestOrtb> request = HttpRequest.<AlvadsRequestOrtb>builder()
                .payload(createAlvadsRequest("imp-banner", 300, 250))
                .build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(
                BidderCall.succeededHttp(request, httpResponse, null),
                bidRequest
        );

        // then - banner bid
        final BidderBid bannerBidderBid = result.getValue().stream()
                .filter(b -> b.getBid().getImpid().equals("imp-banner"))
                .findFirst()
                .orElseThrow();

        // then - video bid
        final BidderBid videoBidderBid = result.getValue().stream()
                .filter(b -> b.getBid().getImpid().equals("imp-video"))
                .findFirst()
                .orElseThrow();
    }

    private static Bid createBid(String id, String impId, double price) {
        return Bid.builder()
                .id(id)
                .impid(impId)
                .price(BigDecimal.valueOf(price))
                .build();
    }

    private static SeatBid createSeatBid(Bid... bids) {
        return SeatBid.builder()
                .bid(Arrays.asList(bids))
                .build();
    }

    private static BidResponse createBidResponse(List<SeatBid> seatBids, String currency) {
        return BidResponse.builder()
                .seatbid(seatBids)
                .cur(currency)
                .build();
    }

    private static Imp createImp(String id, String publisherId, int width, int height) {
        final ObjectNode extNode = new ObjectMapper().createObjectNode()
                .putObject("bidder")
                .put("publisherUniqueId", publisherId);

        return Imp.builder()
                .id(id)
                .banner(Banner.builder().w(width).h(height).build())
                .video(height > 250 ? com.iab.openrtb.request.Video.builder().w(width).h(height).build() : null)
                .ext(extNode)
                .build();
    }

    private static BidRequest createBidRequest(List<Imp> imps) {
        return BidRequest.builder()
                .id("req-123")
                .imp(imps)
                .build();
    }

    private static AlvadsRequestOrtb createAlvadsRequest(String impId, int width, int height) {
        final AlvaAdsImp alvaImp = AlvaAdsImp.builder()
                .id(impId)
                .banner(height <= 250 ? Map.of("w", width, "h", height) : null)
                .video(height > 250 ? Map.of("w", width, "h", height) : null)
                .build();

        return AlvadsRequestOrtb.builder()
                .imp(List.of(alvaImp))
                .build();
    }
}
