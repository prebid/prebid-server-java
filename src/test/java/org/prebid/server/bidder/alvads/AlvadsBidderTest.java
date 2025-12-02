package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class AlvadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://helios-ads-qa-core.ssidevops.com/decision/openrtb";

    private final AlvadsBidder target = new AlvadsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlvadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForInvalidImpExt() {
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
    public void makeHttpRequestsShouldBuildValidHttpRequestsUrl() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .allMatch(uri -> uri.equals(ENDPOINT_URL));
    }

    @Test
    public void makeHttpRequestsShouldBuildValidHttpRequestsHeaders() {
        // given
        final Imp bannerImp = createImp("imp-banner", "pub-1", 300, 250);
        final BidRequest bidRequest = createBidRequest(List.of(bannerImp));

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
    }

    @Test
    public void makeHttpRequestsShouldBuildValidHttpRequestsImpIds() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getImpIds()).contains("imp-1");
        assertThat(result.getValue().get(1).getImpIds()).contains("imp-2");
    }

    @Test
    public void makeHttpRequestsShouldBuildValidHttpRequestsImpContent() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HttpRequest<AlvadsRequestOrtb> req1 = result.getValue().get(0);
        final HttpRequest<AlvadsRequestOrtb> req2 = result.getValue().get(1);

        assertThat(req1.getPayload().getImp())
                .hasSize(1)
                .extracting("id", "banner", "video")
                .containsExactly(tuple("imp-1", Map.of("w", 300, "h", 250), null));

        assertThat(req2.getPayload().getImp())
                .hasSize(1)
                .extracting("id", "banner", "video")
                .containsExactly(tuple("imp-2", null, Map.of("w", 640, "h", 480)));
    }

    @Test
    public void makeHttpRequestsShouldBuildValidHttpRequestsFromInput() {
        // given
        final BidRequest bidRequest = createBidRequestWithBannerAndVideo();

        // when
        final Result<List<HttpRequest<AlvadsRequestOrtb>>> result = target.makeHttpRequests(bidRequest);

        // then
        final HttpRequest<AlvadsRequestOrtb> req1 = result.getValue().get(0);
        final HttpRequest<AlvadsRequestOrtb> req2 = result.getValue().get(1);

        assertThat(req1.getPayload().getId()).isEqualTo("req-123");
        assertThat(req2.getPayload().getId()).isEqualTo("req-123");

        assertThat(req1.getPayload().getSite().getPage())
                .isEqualTo("https://example.com");
        assertThat(req2.getPayload().getSite().getPage())
                .isEqualTo("https://example.com");

        Device expectedDevice = bidRequest.getDevice();
        assertThat(req1.getPayload().getDevice()).isEqualTo(expectedDevice);
        assertThat(req2.getPayload().getDevice()).isEqualTo(expectedDevice);

        User expectedUser = bidRequest.getUser();
        assertThat(req1.getPayload().getUser()).isEqualTo(expectedUser);
        assertThat(req2.getPayload().getUser()).isEqualTo(expectedUser);

        Regs expectedRegs = bidRequest.getRegs();
        assertThat(req1.getPayload().getRegs()).isEqualTo(expectedRegs);
        assertThat(req2.getPayload().getRegs()).isEqualTo(expectedRegs);

        assertThat(req1.getPayload().getImp())
                .hasSize(1)
                .extracting("id", "banner", "video")
                .containsExactly(
                        tuple("imp-1",
                                Map.of("w", 300, "h", 250),
                                null));

        assertThat(req2.getPayload().getImp())
                .hasSize(1)
                .extracting("id", "banner", "video")
                .containsExactly(
                        tuple("imp-2",
                                null,
                                Map.of("w", 640, "h", 480)));

    }

    @Test
    public void makeBidsShouldReturnEmptyListForEmptyResponse() {
        // given
        final BidderCall<AlvadsRequestOrtb> call = buildBidderCall(List.of(), List.of(), "USD");

        // when
        final Result<List<BidderBid>> result = target.makeBids(call, createBidRequest(List.of()));

        // then
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidderBid() {
        // given
        final Imp bannerImp = createImp("imp-banner", "pub-1", 300, 250);
        final BidRequest bidRequest = createBidRequest(List.of(bannerImp));

        final Bid bannerBid = createBid("bid-banner", "imp-banner", 1.5);
        final SeatBid seatBid = createSeatBid(bannerBid);

        final BidderCall<AlvadsRequestOrtb> call = buildBidderCall(
                List.of(createAlvadsRequestImp("imp-banner", 300, 250)),
                List.of(seatBid),
                "USD");

        // when
        final Result<List<BidderBid>> result = target.makeBids(call, bidRequest);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidderBid() {
        // given
        final Imp videoImp = createImp("imp-video", "pub-2", 640, 480);
        final BidRequest bidRequest = createBidRequest(List.of(videoImp));

        final Bid videoBid = createBid("bid-video", "imp-video", 2.5);
        final SeatBid seatBid = createSeatBid(videoBid);

        final BidderCall<AlvadsRequestOrtb> call = buildBidderCall(
                List.of(createAlvadsRequestImp("imp-video", 640, 480)),
                List.of(seatBid),
                "USD");

        // when
        final Result<List<BidderBid>> result = target.makeBids(call, bidRequest);

        // then
        assertThat(result.getValue()).containsExactly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldIgnoreUnsupportedBidType() {
        // given
        final Imp unknownImp = createImp("imp-unknown", "pub-3", 100, 100);
        final BidRequest bidRequest = createBidRequest(List.of(unknownImp));

        final Bid unknownBid = createBid("bid-unknown", "imp-unknown", 1.0);
        final SeatBid seatBid = createSeatBid(unknownBid);

        final BidderCall<AlvadsRequestOrtb> call = buildBidderCall(List.of(), List.of(seatBid), "USD");

        // when
        final Result<List<BidderBid>> result = target.makeBids(call, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest createBidRequestWithBannerAndVideo() {
        final ObjectNode bidderNode1 = jacksonMapper.mapper().createObjectNode();
        bidderNode1.put("publisherUniqueId", "pub-1");
        bidderNode1.put("endpointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode1 = jacksonMapper.mapper().createObjectNode();
        impExtNode1.set("bidder", bidderNode1);

        final ObjectNode bidderNode2 = jacksonMapper.mapper().createObjectNode();
        bidderNode2.put("publisherUniqueId", "pub-2");
        bidderNode2.put("endpointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode2 = jacksonMapper.mapper().createObjectNode();
        impExtNode2.set("bidder", bidderNode2);

        final Imp imp1 = Imp.builder()
                .id("imp-1")
                .banner(Banner.builder().w(300).h(250).build())
                .ext(impExtNode1)
                .build();

        final Imp imp2 = Imp.builder()
                .id("imp-2")
                .video(Video.builder().w(640).h(480).build())
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
        final ObjectNode bidderNode = jacksonMapper.mapper().createObjectNode()
                .put("publisherUniqueId", publisherId);

        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.set("bidder", bidderNode);

        return Imp.builder()
                .id(id)
                .banner(Banner.builder().w(width).h(height).build())
                .video(height > 250 ? Video.builder().w(width).h(height).build() : null)
                .ext(extNode)
                .build();
    }

    private static BidRequest createBidRequest(List<Imp> imps) {
        return BidRequest.builder()
                .id("req-123")
                .imp(imps)
                .build();
    }

    private static AlvaAdsImp createAlvadsRequestImp(String impId, int width, int height) {
        return AlvaAdsImp.builder()
                .id(impId)
                .banner(height <= 250 ? Map.of("w", width, "h", height) : null)
                .video(height > 250 ? Map.of("w", width, "h", height) : null)
                .build();
    }

    private static BidderCall<AlvadsRequestOrtb> buildBidderCall(
            List<AlvaAdsImp> imps,
            List<SeatBid> seatBids,
            String currency) {

        final BidResponse bidResponse = createBidResponse(seatBids, currency);

        final HttpResponse httpResponse = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final HttpRequest<AlvadsRequestOrtb> request = HttpRequest.<AlvadsRequestOrtb>builder()
                .payload(AlvadsRequestOrtb.builder()
                        .imp(imps)
                        .build())
                .build();

        return BidderCall.succeededHttp(request, httpResponse, null);
    }

}
