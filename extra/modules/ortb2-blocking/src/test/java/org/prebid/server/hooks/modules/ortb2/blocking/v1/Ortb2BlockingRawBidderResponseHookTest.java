package org.prebid.server.hooks.modules.ortb2.blocking.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.RejectedBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attribute;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.AttributeActionOverrides;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.BooleanOverride;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Conditions;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ModuleConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.model.ModuleContext;
import org.prebid.server.hooks.modules.ortb2.blocking.v1.model.BidderInvocationContextImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_ADVERTISER_BLOCKED;

@ExtendWith(MockitoExtension.class)
public class Ortb2BlockingRawBidderResponseHookTest {

    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final Ortb2BlockingRawBidderResponseHook hook = new Ortb2BlockingRawBidderResponseHook(
            ObjectMapperProvider.mapper());

    @Test
    public void shouldReturnResultWithNoActionWhenNoBidsBlocked() {
        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", null, true));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create())
                .analyticsTags(TagsImpl.of(singletonList(ActivityImpl.of(
                        "enforce-blocking",
                        "success",
                        singletonList(ResultImpl.of(
                                "success-allow",
                                null,
                                AppliedToImpl.builder()
                                        .bidders(singletonList("bidder1"))
                                        .impIds(singletonList("impId1"))
                                        .build()))))))
                .build());
    }

    @Test
    public void shouldReturnResultWithNoActionAndErrorWhenInvalidAccountConfig() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode()
                .put("attributes", 1);

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create())
                .errors(singletonList("attributes field in account configuration is not an object"))
                .build());
    }

    @Test
    public void shouldReturnResultWithNoActionAndNoErrorWhenInvalidAccountConfigAndDebugDisabled() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode()
                .put("attributes", 1);

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, false));

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(InvocationResultImpl.builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .moduleContext(ModuleContext.create())
                .build());
    }

    @Test
    public void shouldReturnResultWithPayloadUpdateAndAnalyticsTags() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        final BidderBid bid1 = bid(bid -> bid.id("bidId1"));
        final BidderBid bid2 = bid(bid -> bid.id("bidId2").adomain(singletonList("domain1.com")));
        final BidderBid bid3 = bid(bid -> bid.id("bidId2").adomain(singletonList("domain2.com")));

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(asList(bid1, bid2, bid3)),
                BidderInvocationContextImpl.builder()
                        .bidder("bidder1")
                        .accountConfig(accountConfig)
                        .auctionContext(AuctionContext.builder().build())
                        .moduleContext(ModuleContext.create().with(
                                "bidder1", BlockedAttributes.builder().badv(singletonList("domain2.com")).build()))
                        .debugEnabled(true)
                        .build());

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderResponsePayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.warnings()).isNull();
            softly.assertThat(invocationResult.errors()).isNull();
        });

        final PayloadUpdate<BidderResponsePayload> payloadUpdate = invocationResult.payloadUpdate();
        final BidderResponsePayloadImpl payloadToUpdate = BidderResponsePayloadImpl.of(asList(bid1, bid2, bid3));
        assertThat(payloadUpdate.apply(payloadToUpdate)).isEqualTo(BidderResponsePayloadImpl.of(
                singletonList(bid2)));

        assertThat(invocationResult.analyticsTags()).isEqualTo(TagsImpl.of(singletonList(ActivityImpl.of(
                "enforce-blocking",
                "success",
                asList(
                        ResultImpl.of(
                                "success-blocked",
                                mapper.createObjectNode()
                                        .<ObjectNode>set("adomain", mapper.createArrayNode())
                                        .set("attributes", mapper.createArrayNode()
                                                .add("badv")),
                                AppliedToImpl.builder()
                                        .bidders(singletonList("bidder1"))
                                        .impIds(singletonList("impId1"))
                                        .build()),
                        ResultImpl.of(
                                "success-allow",
                                null,
                                AppliedToImpl.builder()
                                        .bidders(singletonList("bidder1"))
                                        .impIds(singletonList("impId1"))
                                        .build()),
                        ResultImpl.of(
                                "success-blocked",
                                mapper.createObjectNode()
                                        .<ObjectNode>set("adomain", mapper.createArrayNode()
                                                .add("domain2.com"))
                                        .set("attributes", mapper.createArrayNode()
                                                .add("badv")),
                                AppliedToImpl.builder()
                                        .bidders(singletonList("bidder1"))
                                        .impIds(singletonList("impId1"))
                                        .build()))))));

        assertThat(invocationResult.rejections()).containsOnly(entry("bidder1", List.of(
                        RejectedBid.of(bid1,
                                RESPONSE_REJECTED_ADVERTISER_BLOCKED),
                        RejectedBid.of(bid(bid -> bid.id("bidId2").adomain(singletonList("domain2.com"))),
                                RESPONSE_REJECTED_ADVERTISER_BLOCKED))));
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndWarning() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .actionOverrides(AttributeActionOverrides.blockUnknown(
                                asList(
                                        BooleanOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                true),
                                        BooleanOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                false))))
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderResponsePayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.warnings()).containsOnly(
                    "More than one conditions matches request. Bidder: bidder1, request media types: [banner]");
            softly.assertThat(invocationResult.errors()).isNull();
        });
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndNoWarningWhenDebugDisabled() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .actionOverrides(AttributeActionOverrides.blockUnknown(
                                asList(
                                        BooleanOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                true),
                                        BooleanOverride.of(
                                                Conditions.of(singletonList("bidder1"), null),
                                                false))))
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, false));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderResponsePayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.warnings()).isNull();
            softly.assertThat(invocationResult.errors()).isNull();
        });
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndDebugMessage() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, true));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderResponsePayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.debugMessages()).containsOnly(
                    "Bid 0 from bidder bidder1 has been rejected, failed checks: [badv]");
        });
    }

    @Test
    public void shouldReturnResultWithUpdateActionAndNoDebugMessageWhenDebugDisabled() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .enforceBlocks(true)
                        .blockUnknown(true)
                        .build())
                .build()));

        // when
        final Future<InvocationResult<BidderResponsePayload>> result = hook.call(
                BidderResponsePayloadImpl.of(singletonList(bid())),
                BidderInvocationContextImpl.of("bidder1", accountConfig, false));

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<BidderResponsePayload> invocationResult = result.result();
        assertSoftly(softly -> {
            softly.assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            softly.assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            softly.assertThat(invocationResult.debugMessages()).isNull();
        });
    }

    private static BidderBid bid() {
        return bid(bid -> bid.id("bidId"));
    }

    private static BidderBid bid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidderBid.of(
                bidCustomizer.apply(Bid.builder().impid("impId1")).build(),
                BidType.banner,
                "USD");
    }

    private static ObjectNode toObjectNode(ModuleConfig config) {
        return mapper.valueToTree(config);
    }
}
