package org.prebid.server.hooks.modules.rule.engine.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.rule.engine.core.config.RuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
class PbRuleEngineProcessedAuctionRequestHookTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PbRuleEngineProcessedAuctionRequestHook target;

    @Mock(strictness = LENIENT)
    private RuleParser ruleParser;

    @Mock(strictness = LENIENT)
    private AuctionRequestPayload payload;

    @Mock(strictness = LENIENT)
    private AuctionInvocationContext invocationContext;

    @Mock(strictness = LENIENT)
    private Rule<BidRequest, RequestRuleContext> processedAuctionRequestRule;

    @Mock(strictness = LENIENT)
    private BidRequest bidRequest;

    @Mock(strictness = LENIENT)
    private Tags tags;

    private final AuctionContext auctionContext = AuctionContext.builder().account(Account.empty("1001")).build();

    @BeforeEach
    void setUp() {
        target = new PbRuleEngineProcessedAuctionRequestHook(ruleParser, "datacenter");

        given(invocationContext.auctionContext()).willReturn(auctionContext);
        given(payload.bidRequest()).willReturn(bidRequest);

        given(ruleParser.parseForAccount(any(), any())).willReturn(
                Future.succeededFuture(
                        PerStageRule.builder()
                                .timestamp(Instant.EPOCH)
                                .processedAuctionRequestRule(processedAuctionRequestRule)
                                .build()));
    }

    @Test
    public void callShouldReturnNoActionWhenNoAccountConfigProvided() {
        // when and then
        assertThat(target.call(payload, invocationContext).result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
            assertThat(invocationResult.payloadUpdate()).isNull();
        });
    }

    @Test
    public void callShouldReturnNoActionWhenRuleActionIsNoAction() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.createObjectNode());
        given(processedAuctionRequestRule.process(
                bidRequest,
                RequestRuleContext.of(auctionContext, Granularity.Request.instance(), "datacenter")))
                .willReturn(RuleResult.noAction(bidRequest));

        // when and then
        assertThat(target.call(payload, invocationContext).result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_action);
            assertThat(invocationResult.payloadUpdate()).isNull();
        });
    }

    @Test
    public void callShouldReturnPayloadUpdateWhenRuleActionIsUpdate() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.createObjectNode());
        given(processedAuctionRequestRule.process(
                bidRequest,
                RequestRuleContext.of(auctionContext, Granularity.Request.instance(), "datacenter")))
                .willReturn(RuleResult.of(bidRequest, RuleAction.UPDATE, tags, Collections.emptyList()));

        // when and then
        assertThat(target.call(payload, invocationContext).result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.update);
            assertThat(invocationResult.payloadUpdate()).isNotNull();
            assertThat(invocationResult.analyticsTags()).isEqualTo(tags);
        });
    }

    @Test
    public void callShouldReturnRejectWhenRuleActionIsReject() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.createObjectNode());
        given(processedAuctionRequestRule.process(
                bidRequest,
                RequestRuleContext.of(auctionContext, Granularity.Request.instance(), "datacenter")))
                .willReturn(RuleResult.rejected(tags, Collections.emptyList()));

        // when and then
        assertThat(target.call(payload, invocationContext).result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.success);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.reject);
            assertThat(invocationResult.payloadUpdate()).isNull();
            assertThat(invocationResult.analyticsTags()).isEqualTo(tags);
        });
    }

    @Test
    public void callShouldReturnFailureOnFailure() {
        // given
        given(invocationContext.accountConfig()).willReturn(MAPPER.createObjectNode());
        given(processedAuctionRequestRule.process(
                bidRequest,
                RequestRuleContext.of(auctionContext, Granularity.Request.instance(), "datacenter")))
                .willThrow(PreBidException.class);

        // when and then
        assertThat(target.call(payload, invocationContext).result()).satisfies(invocationResult -> {
            assertThat(invocationResult.status()).isEqualTo(InvocationStatus.failure);
            assertThat(invocationResult.action()).isEqualTo(InvocationAction.no_invocation);
            assertThat(invocationResult.payloadUpdate()).isNull();
        });
    }
}
