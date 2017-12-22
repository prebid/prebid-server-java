package org.rtb.vexing.auction;

import org.junit.Test;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.settings.model.Account;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.*;

public class TargetingKeywordsTest {

    private final PreBidRequest preBidRequest = PreBidRequest.builder().maxKeyLength(20).build();

    @Test
    public void shouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> TargetingKeywords.addTargetingKeywords(null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> TargetingKeywords.addTargetingKeywords(preBidRequest, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> TargetingKeywords.addTargetingKeywords(preBidRequest, emptyList(), null));
    }

    @Test
    public void shouldReturnBidsWithTargetingKeywords() {
        // given
        final List<Bid> bids = asList(
                Bid.builder().bidder("bidder1").price(BigDecimal.ONE).dealId("dealId1").cacheId("cacheId1").width(50)
                        .height(100).build(),
                Bid.builder().bidder("veryververyverylongbidder1").price(BigDecimal.ONE).dealId("dealId2")
                        .cacheId("cacheId2").width(180).height(250).build());

        // when
        final List<Bid> bidsWithKeywords = TargetingKeywords.addTargetingKeywords(preBidRequest, bids,
                Account.builder().priceGranularity("low").build());

        // then
        assertThat(bidsWithKeywords).hasSize(2);
        assertThat(bidsWithKeywords.get(0).adServerTargeting).containsOnly(
                entry("hb_pb_bidder1", "1.00"),
                entry("hb_bidder_bidder1", "bidder1"),
                entry("hb_cache_id_bidder1", "cacheId1"),
                entry("hb_size_bidder1", "50x100"),
                entry("hb_deal_bidder1", "dealId1"),
                entry("hb_pb", "1.00"),
                entry("hb_bidder", "bidder1"),
                entry("hb_cache_id", "cacheId1"),
                entry("hb_size", "50x100"),
                entry("hb_deal", "dealId1"),
                entry("hb_creative_loadtype", "html"));
        assertThat(bidsWithKeywords.get(1).adServerTargeting).containsOnly(
                entry("hb_pb_veryververyver", "1.00"),
                entry("hb_bidder_veryverver", "veryververyverylongbidder1"),
                entry("hb_cache_id_veryverv", "cacheId2"),
                entry("hb_size_veryververyv", "180x250"),
                entry("hb_deal_veryververyv", "dealId2"));
    }

    @Test
    public void shouldUseDefaultPriceGranularity() {
        // given
        final List<Bid> bids = singletonList(Bid.builder().bidder("").price(BigDecimal.valueOf(3.87)).build());

        // when
        final List<Bid> bidsWithKeywords = TargetingKeywords.addTargetingKeywords(preBidRequest, bids,
                Account.builder().build());

        // then
        assertThat(bidsWithKeywords).hasSize(1)
                .element(0).returns("3.80", bid -> bid.adServerTargeting.get("hb_pb"));
    }

    @Test
    public void shouldReturnDemandSdkLoadtypeForAudienceNetworkBidder() {
        // given
        final List<Bid> bids = singletonList(Bid.builder().bidder("audienceNetwork").price(BigDecimal.ONE).build());

        // when
        final List<Bid> bidsWithKeywords = TargetingKeywords.addTargetingKeywords(preBidRequest, bids,
                Account.builder().build());

        // then
        assertThat(bidsWithKeywords).hasSize(1)
                .element(0).returns("demand_sdk", bid -> bid.adServerTargeting.get("hb_creative_loadtype"));
    }

    @Test
    public void shouldNotIncludeDealIdAndSize() {
        // given
        final List<Bid> bids = singletonList(
                Bid.builder().bidder("bidder").price(BigDecimal.ONE).cacheId("cacheId1").build());

        // when
        final List<Bid> bidsWithKeywords = TargetingKeywords.addTargetingKeywords(preBidRequest, bids,
                Account.builder().build());

        // then
        assertThat(bidsWithKeywords).hasSize(1);
        assertThat(bidsWithKeywords.get(0).adServerTargeting)
                .doesNotContainKeys("hb_size_bidder", "hb_deal_bidder", "hb_size", "hb_deal");
    }

    @Test
    public void shouldAddTargetingKeywordsFromBidderResponse() {
        // given
        final Map<String, String> keywords = new HashMap();
        keywords.put("rpfl_1001", "2_tier0100");
        final List<Bid> bids = singletonList(Bid.builder().bidder("bidder").price(BigDecimal.ONE).cacheId("cacheId1")
                .adServerTargeting(keywords).build());

        // when
        final List<Bid> bidsWithKeywords = TargetingKeywords.addTargetingKeywords(preBidRequest, bids,
                Account.builder().build());

        // then
        assertThat(bidsWithKeywords.get(0).adServerTargeting).containsOnly(
                entry("hb_pb", "1.00"),
                entry("hb_pb_bidder", "1.00"),
                entry("hb_bidder", "bidder"),
                entry("hb_cache_id", "cacheId1"),
                entry("hb_bidder_bidder", "bidder"),
                entry("hb_creative_loadtype", "html"),
                entry("hb_cache_id_bidder", "cacheId1"),
                entry("rpfl_1001", "2_tier0100"));
    }
}