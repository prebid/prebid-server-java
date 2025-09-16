package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.alvads.model.AlvaAdsImp;
import org.prebid.server.bidder.alvads.model.AlvadsRequestORTB;
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

        final ObjectNode extNode = new ObjectMapper().createObjectNode();
        extNode.put("bidder", "invalid");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id("1").ext(extNode).build()))
                .build();

        final Result<List<HttpRequest<AlvadsRequestORTB>>> result = target.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage())
                .contains("Missing or invalid bidder ext");
    }

    @Test
    void makeHttpRequestsShouldBuildValidHttpRequests() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode bidderNode1 = mapper.createObjectNode();
        bidderNode1.put("publisherUniqueId", "pub-1");
        bidderNode1.put("endPointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode1 = mapper.createObjectNode();
        impExtNode1.set("bidder", bidderNode1);

        final ObjectNode bidderNode2 = mapper.createObjectNode();
        bidderNode2.put("publisherUniqueId", "pub-2");
        bidderNode2.put("endPointUrl", ENDPOINT_URL);

        final ObjectNode impExtNode2 = mapper.createObjectNode();
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

        final Site site = Site.builder()
                .page("https://example.com")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("req-123")
                .imp(List.of(imp1, imp2))
                .site(site)
                .device(com.iab.openrtb.request.Device.builder().build())
                .build();

        final Result<List<HttpRequest<AlvadsRequestORTB>>> result = target.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);

        final HttpRequest<AlvadsRequestORTB> request1 = result.getValue().get(0);
        final HttpRequest<AlvadsRequestORTB> request2 = result.getValue().get(1);

        assertThat(request1.getUri()).isEqualTo(ENDPOINT_URL);
        assertThat(request2.getUri()).isEqualTo(ENDPOINT_URL);

        assertThat(request1.getHeaders()).isNotEmpty();
        assertThat(request2.getHeaders()).isNotEmpty();

        assertThat(request1.getImpIds()).contains("imp-1");
        assertThat(request2.getImpIds()).contains("imp-2");

        assertThat(request1.getPayload().getImp().get(0).getBanner()).isNotNull();
        assertThat(request1.getPayload().getImp().get(0).getVideo()).isNull();

        assertThat(request2.getPayload().getImp().get(0).getVideo()).isNotNull();
        assertThat(request2.getPayload().getImp().get(0).getBanner()).isNull();

        assertThat(request1.getPayload().getId()).isEqualTo("req-123");
        assertThat(request2.getPayload().getId()).isEqualTo("req-123");
    }

    @Test
    void makeBidsShouldReturnEmptyListForEmptyResponse() {
        final BidResponse bidResponse = BidResponse.builder().build();
        final HttpResponse response = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final BidderCall<AlvadsRequestORTB> call = BidderCall.succeededHttp(
                HttpRequest.<AlvadsRequestORTB>builder().payload(null).build(),
                response,
                null
        );

        final Result<List<BidderBid>> result = target.makeBids(call, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
    }

    @Test
    void makeBidsShouldReturnBidderBids() {
        // GIVEN
        final String impId = "AE_AD_1748977459403";
        final String publisherId = "D7DACCE3-C23D-4AB9-8FE6-9FF41BF32F8F";

        final Bid bid = createBid("bid1", impId, 1);
        final SeatBid seatBid = createSeatBid(bid);
        final BidResponse bidResponse = createBidResponse(List.of(seatBid), "USD");

        final HttpResponse response = HttpResponse.of(
                200,
                MultiMap.caseInsensitiveMultiMap(),
                jacksonMapper.encodeToString(bidResponse)
        );

        final Imp imp = createImp(impId, publisherId, 320, 100);
        final BidRequest bidRequest = createBidRequest(List.of(imp));

        final AlvadsRequestORTB alvadsRequest = createAlvadsRequest(impId, 320, 100);
        final HttpRequest<AlvadsRequestORTB> httpRequest = HttpRequest.<AlvadsRequestORTB>builder()
                .payload(alvadsRequest)
                .build();

        final BidderCall<AlvadsRequestORTB> call = BidderCall.succeededHttp(httpRequest, response, null);

        // WHEN
        final Result<List<BidderBid>> result = target.makeBids(call, bidRequest);

        // THEN
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBid().getId()).isEqualTo("bid1");
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
                .ext(extNode)
                .build();
    }

    private static BidRequest createBidRequest(List<Imp> imps) {
        return BidRequest.builder()
                .id("req-123")
                .imp(imps)
                .build();
    }

    private static AlvadsRequestORTB createAlvadsRequest(String impId, int width, int height) {
        final AlvaAdsImp alvaImp = AlvaAdsImp.builder()
                .id(impId)
                .banner(Map.of("w", width, "h", height))
                .build();

        return AlvadsRequestORTB.builder()
                .imp(List.of(alvaImp))
                .build();
    }

}
