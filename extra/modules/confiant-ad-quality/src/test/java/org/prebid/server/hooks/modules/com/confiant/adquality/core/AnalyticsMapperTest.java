package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class AnalyticsMapperTest {

    @Test
    public void shouldMapBidsScanResultToAnalyticsTags() {
        // given
        final List<BidderResponse> bidderResponsesWithIssues = List.of(
                AdQualityModuleTestUtils.getBidderResponse("bidder_a", "imp_a", "bid_id_a"));

        final List<BidderResponse> bidderResponsesWithoutIssues = List.of(
                AdQualityModuleTestUtils.getBidderResponse("bidder_b", "imp_b", "bid_id_b"));

        final List<BidderResponse> bidderResponsesNotScanned = List.of(
                AdQualityModuleTestUtils.getBidderResponse("bidder_c", "imp_c", "bid_id_c"),
                AdQualityModuleTestUtils.getBidderResponse("bidder_d", "imp_d", "bid_id_d"));

        // when
        final Tags tags = AnalyticsMapper.toAnalyticsTags(bidderResponsesWithIssues, bidderResponsesWithoutIssues, bidderResponsesNotScanned);

        // then
        assertThat(tags.activities()).isEqualTo(singletonList(ActivityImpl.of(
                "ad-scan", "success", List.of(
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
}
