package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ConditionMatchingRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.ListUtil;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class PerImpMatchingRuleTest {

    private PerImpMatchingRule target;

    @Mock(strictness = LENIENT)
    private ConditionMatchingRule<BidRequest, RequestRuleContext> conditionMatchingRule;

    @BeforeEach
    public void setUp() {
        target = new PerImpMatchingRule(conditionMatchingRule);
    }

    @Test
    public void processShouldRunMatchingRulePerImpAndCombineResults() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().id("1").build(), Imp.builder().id("2").build()))
                .build();

        final RequestRuleContext firstImpContext = RequestRuleContext.of(
                AuctionContext.builder().build(),
                new Granularity.Imp("1"),
                null);

        final BidRequest updatedBidRequest = bidRequest.toBuilder().id("updated").build();
        final List<Activity> firstActivities = singletonList(ActivityImpl.of("activity1", "success", emptyList()));
        final List<SeatNonBid> firstSeatNonBids = singletonList(
                SeatNonBid.of("seat1", singletonList(NonBid.of("1", BidRejectionReason.NO_BID))));
        given(conditionMatchingRule.process(bidRequest, firstImpContext)).willReturn(
                RuleResult.of(
                        UpdateResult.updated(updatedBidRequest),
                        TagsImpl.of(firstActivities),
                        firstSeatNonBids));

        final RequestRuleContext secondImpContext = RequestRuleContext.of(
                AuctionContext.builder().build(),
                new Granularity.Imp("2"),
                null);

        final BidRequest resultBidRequest = bidRequest.toBuilder().id("updated2").build();
        final List<Activity> secondActivities = singletonList(ActivityImpl.of("activity2", "success", emptyList()));
        final List<SeatNonBid> secondSeatNonBids = singletonList(
                SeatNonBid.of("seat2", singletonList(NonBid.of("2", BidRejectionReason.NO_BID))));
        given(conditionMatchingRule.process(updatedBidRequest, secondImpContext)).willReturn(
                RuleResult.of(
                        UpdateResult.updated(resultBidRequest),
                        TagsImpl.of(secondActivities),
                        secondSeatNonBids));

        final RequestRuleContext requestContext = RequestRuleContext.of(
                AuctionContext.builder().build(),
                Granularity.Request.instance(),
                null);

        // when and then
        assertThat(target.process(bidRequest, requestContext)).isEqualTo(
                RuleResult.of(
                        UpdateResult.updated(resultBidRequest),
                        TagsImpl.of(ListUtil.union(firstActivities, secondActivities)),
                        ListUtil.union(firstSeatNonBids, secondSeatNonBids)));
    }
}
