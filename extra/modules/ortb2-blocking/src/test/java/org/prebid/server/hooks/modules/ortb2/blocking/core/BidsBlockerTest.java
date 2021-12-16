package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attribute;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ModuleConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.AnalyticsResult;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedBids;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

class BidsBlockerTest {

    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    public void shouldReturnEmptyResultWhenNoBlockingResponseConfig() {
        // given
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", null, null, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnEmptyResultWithErrorWhenInvalidAccountConfig() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode()
                .put("attributes", 1);

        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, true);

        // when and then
        assertThat(blocker.block()).isEqualTo(ExecutionResult.builder()
                .errors(singletonList("attributes field in account configuration is not an object"))
                .build());
    }

    @Test
    public void shouldReturnEmptyResultWithoutErrorWhenInvalidAccountConfigAndDebugDisabled() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode()
                .put("attributes", 1);

        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, false);

        // when and then
        assertThat(blocker.block()).isEqualTo(ExecutionResult.empty());
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithoutAdomainAndBlockUnknownFalse() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(false)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithoutAdomainAndEnforceBlocksFalseAndBlockUnknownTrue() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(false)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnResultWithBidWhenBidWithoutAdomainAndBlockUnknownTrue() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, false);

        // when and then
        assertThat(blocker.block()).satisfies(result -> hasValue(result, 0));
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithBlockedAdomainAndEnforceBlocksFalse() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(false)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BlockedAttributes blockedAttributes = attributesWithBadv(singletonList("domain1.com"));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithNotBlockedAdomain() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BlockedAttributes blockedAttributes = attributesWithBadv(singletonList("domain2.com"));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnResultWithBidWhenBidWithBlockedAdomainAndEnforceBlocksTrue() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BlockedAttributes blockedAttributes = attributesWithBadv(singletonList("domain1.com"));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, false);

        // when and then
        assertThat(blocker.block()).satisfies(result -> hasValue(result, 0));
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithAdomainAndNoBlockedAttributes() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithAttrAndNoBlockedBannerAttrForImp() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .battr(Attribute.battrBuilder()
                        .enforceBlocks(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid
                .impid("impId2")
                .attr(singletonList(1))));
        final BlockedAttributes blockedAttributes = BlockedAttributes.builder()
                .battr(singletonMap("impId1", asList(1, 2)))
                .build();
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnEmptyResultWhenBidWithBlockedAdomainAndInDealsExceptions() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .allowedForDeals(singletonList("domain1.com"))
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BlockedAttributes blockedAttributes = attributesWithBadv(singletonList("domain1.com"));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(BidsBlockerTest::isEmpty);
    }

    @Test
    public void shouldReturnResultWithBidWhenBidWithBlockedAdomainAndNotInDealsExceptions() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .allowedForDeals(singletonList("domain2.com"))
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid(bid -> bid.adomain(singletonList("domain1.com"))));
        final BlockedAttributes blockedAttributes = attributesWithBadv(singletonList("domain1.com"));
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, false);

        // when and then
        assertThat(blocker.block()).satisfies(result -> hasValue(result, 0));
    }

    @Test
    public void shouldReturnResultWithBidAndDebugMessageWhenBidIsBlocked() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .bcat(Attribute.bcatBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, true);

        // when and then
        assertThat(blocker.block()).satisfies(result -> {
            assertThat(result.getValue()).isEqualTo(BlockedBids.of(singleton(0)));
            assertThat(result.getDebugMessages()).containsOnly(
                    "Bid 0 from bidder bidder1 has been rejected, failed checks: [bcat]");
        });
    }

    @Test
    public void shouldReturnResultWithBidWithoutDebugMessageWhenBidIsBlockedAndDebugDisabled() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .bcat(Attribute.bcatBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = singletonList(bid());
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, null, false);

        // when and then
        assertThat(blocker.block()).satisfies(result -> hasValue(result, 0));
    }

    @Test
    public void shouldReturnResultWithAnalyticsResults() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .build())
                .bcat(Attribute.bcatBuilder()
                        .enforceBlocks(true)
                        .build())
                .bapp(Attribute.bappBuilder()
                        .enforceBlocks(true)
                        .build())
                .battr(Attribute.battrBuilder()
                        .enforceBlocks(true)
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = asList(
                bid(bid -> bid
                        .impid("impId1")
                        .adomain(asList("domain2.com", "domain3.com", "domain4.com"))
                        .bundle("app2")),
                bid(bid -> bid
                        .impid("impId2")
                        .cat(asList("cat2", "cat3", "cat4"))
                        .attr(asList(2, 3, 4))),
                bid(bid -> bid
                        .impid("impId1")
                        .adomain(singletonList("domain5.com"))
                        .cat(singletonList("cat5"))
                        .bundle("app5")
                        .attr(singletonList(5))));
        final BlockedAttributes blockedAttributes = BlockedAttributes.builder()
                .badv(asList("domain1.com", "domain2.com", "domain3.com"))
                .bcat(asList("cat1", "cat2", "cat3"))
                .bapp(asList("app1", "app2", "app3"))
                .battr(singletonMap("impId2", asList(1, 2, 3)))
                .build();
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(result -> {
            assertThat(result.getValue()).isEqualTo(BlockedBids.of(new HashSet<>(asList(0, 1))));

            final Map<String, Object> analyticsResultValues1 = new HashMap<>();
            analyticsResultValues1.put("attributes", asList("badv", "bapp"));
            analyticsResultValues1.put("adomain", asList("domain2.com", "domain3.com"));
            analyticsResultValues1.put("bundle", "app2");
            final Map<String, Object> analyticsResultValues2 = new HashMap<>();
            analyticsResultValues2.put("attributes", asList("bcat", "battr"));
            analyticsResultValues2.put("bcat", asList("cat2", "cat3"));
            analyticsResultValues2.put("attr", asList(2, 3));

            assertThat(result.getAnalyticsResults()).containsOnly(
                    AnalyticsResult.of("success-blocked", analyticsResultValues1, "bidder1", "impId1"),
                    AnalyticsResult.of("success-blocked", analyticsResultValues2, "bidder1", "impId2"),
                    AnalyticsResult.of("success-allow", null, "bidder1", "impId1"));
        });
    }

    @Test
    public void shouldReturnResultWithoutSomeBidsWhenAllAttributesInConfig() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .allowedForDeals(singletonList("domain2.com"))
                        .build())
                .bcat(Attribute.bcatBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .allowedForDeals(singletonList("cat2"))
                        .build())
                .bapp(Attribute.bappBuilder()
                        .enforceBlocks(true)
                        .allowedForDeals(singletonList("app2"))
                        .build())
                .battr(Attribute.battrBuilder()
                        .enforceBlocks(true)
                        .allowedForDeals(singletonList(2))
                        .build())
                .build()));

        // when
        final List<BidderBid> bids = asList(
                bid(bid -> bid.adomain(singletonList("domain1.com"))),
                bid(bid -> bid.adomain(singletonList("domain2.com")).cat(singletonList("cat1"))),
                bid(bid -> bid.adomain(singletonList("domain2.com")).cat(singletonList("cat2"))),
                bid(bid -> bid.adomain(singletonList("domain2.com")).cat(singletonList("cat2")).bundle("app1")),
                bid(bid -> bid.adomain(singletonList("domain2.com")).cat(singletonList("cat2")).bundle("app2")),
                bid(bid -> bid
                        .adomain(singletonList("domain2.com"))
                        .cat(singletonList("cat2"))
                        .bundle("app2")
                        .attr(singletonList(1))),
                bid(bid -> bid
                        .adomain(singletonList("domain2.com"))
                        .cat(singletonList("cat2"))
                        .bundle("app2")
                        .attr(singletonList(2))),
                bid());
        final BlockedAttributes blockedAttributes = BlockedAttributes.builder()
                .badv(asList("domain1.com", "domain2.com"))
                .bcat(asList("cat1", "cat2"))
                .bapp(asList("app1", "app2"))
                .battr(singletonMap("impId1", asList(1, 2)))
                .build();
        final BidsBlocker blocker = BidsBlocker.create(bids, "bidder1", accountConfig, blockedAttributes, true);

        // when and then
        assertThat(blocker.block()).satisfies(result -> {
            assertThat(result.getValue()).isEqualTo(BlockedBids.of(new HashSet<>(asList(0, 1, 3, 5, 7))));
            assertThat(result.getDebugMessages()).containsOnly(
                    "Bid 0 from bidder bidder1 has been rejected, failed checks: [badv, bcat]",
                    "Bid 1 from bidder bidder1 has been rejected, failed checks: [bcat]",
                    "Bid 3 from bidder bidder1 has been rejected, failed checks: [bapp]",
                    "Bid 5 from bidder bidder1 has been rejected, failed checks: [battr]",
                    "Bid 7 from bidder bidder1 has been rejected, failed checks: [badv, bcat]");
        });
    }

    private static BidderBid bid() {
        return bid(identity());
    }

    private static BidderBid bid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidderBid.of(
                bidCustomizer.apply(Bid.builder()
                                .impid("impId1")
                                .dealid("dealid1"))
                        .build(),
                BidType.banner,
                "USD");
    }

    private static BlockedAttributes attributesWithBadv(List<String> badv) {
        return BlockedAttributes.builder().badv(badv).build();
    }

    private static ObjectNode toObjectNode(ModuleConfig config) {
        return mapper.valueToTree(config);
    }

    private static void isEmpty(ExecutionResult<BlockedBids> result) {
        assertThat(result.hasValue()).isFalse();
        assertThat(result.getErrors()).isNull();
        assertThat(result.getWarnings()).isNull();
        assertThat(result.getDebugMessages()).isNull();
    }

    private static void hasValue(ExecutionResult<BlockedBids> result, Integer... indexes) {
        assertThat(result.getValue()).isEqualTo(BlockedBids.of(new HashSet<>(asList(indexes))));
        assertThat(result.getErrors()).isNull();
        assertThat(result.getWarnings()).isNull();
        assertThat(result.getDebugMessages()).isNull();
    }
}
