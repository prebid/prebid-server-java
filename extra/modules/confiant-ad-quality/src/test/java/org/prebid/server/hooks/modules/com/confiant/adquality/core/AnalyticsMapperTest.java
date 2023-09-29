package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class AnalyticsMapperTest {

    private final RedisParser redisParser = new RedisParser();

    @Test
    public void shouldMapBidsScanResultToAnalyticsTags() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}]],[[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\", \"issues\": []}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);

        final List<BidderResponse> scannedBidderResponses = List.of(
                getBidderResponse("bidder_a", "imp_a", "bid_id_a"),
                getBidderResponse("bidder_b", "imp_b", "bid_id_b"));

        final List<BidderResponse> notScannedBidderResponses = List.of(
                getBidderResponse("bidder_c", "imp_c", "bid_id_c"),
                getBidderResponse("bidder_d", "imp_d", "bid_id_d"));

        // when
        final Tags tags = AnalyticsMapper.toAnalyticsTags(bidsScanResult, scannedBidderResponses, notScannedBidderResponses);

        // then
        assertThat(tags.activities()).isEqualTo(singletonList(ActivityImpl.of(
                "ad-quality-scan", "success", List.of(
                        ResultImpl.of("skipped", null, AppliedToImpl.builder()
                                .bidders(List.of("bidder_c", "bidder_d"))
                                .impIds(List.of("imp_c", "imp_d"))
                                .bidIds(List.of("bid_id_c", "bid_id_d"))
                                .build()),
                        ResultImpl.of("inspected-has-issue", null, AppliedToImpl.builder()
                                .bidders(List.of("bidder_a"))
                                .impIds(List.of("imp_a"))
                                .bidIds(List.of("bid_id_a"))
                                .build()),
                        ResultImpl.of("inspected-no-issues", null, AppliedToImpl.builder()
                                .bidders(List.of("bidder_b"))
                                .impIds(List.of("imp_b"))
                                .bidIds(List.of("bid_id_b"))
                                .build()))
        )));
    }

    private BidderResponse getBidderResponse(String bidderName, String impId, String bidId) {
        return BidderResponse.of(bidderName, BidderSeatBid.builder()
                .bids(Collections.singletonList(BidderBid.builder()
                        .type(BidType.banner)
                        .bid(Bid.builder()
                                .id(bidId)
                                .price(BigDecimal.valueOf(11))
                                .impid(impId)
                                .adm("adm")
                                .adomain(Collections.singletonList("www.example.com"))
                                .build())
                        .build()))
                .build(), 11);
    }
}
