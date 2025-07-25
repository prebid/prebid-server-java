package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CompositeRuleTest {

    private static final Object VALUE = new Object();

    @Test
    public void processShouldAccumulateResultFromAllSubrules() {
        // given
        final Rule<Object, Object> firstRule = (Rule<Object, Object>) mock(Rule.class);
        given(firstRule.process(any(), any())).willAnswer(invocationOnMock -> RuleResult.of(
                UpdateResult.updated(invocationOnMock.getArgument(0)),
                TagsImpl.of(singletonList(ActivityImpl.of("firstActivity", "success", emptyList()))),
                singletonList(SeatNonBid.of("firstSeat", singletonList(NonBid.of("1", BidRejectionReason.NO_BID))))));

        final Rule<Object, Object> secondRule = (Rule<Object, Object>) mock(Rule.class);
        given(secondRule.process(any(), any())).willAnswer(invocationOnMock -> RuleResult.of(
                UpdateResult.updated(invocationOnMock.getArgument(0)),
                TagsImpl.of(singletonList(ActivityImpl.of("secondActivity", "success", emptyList()))),
                singletonList(SeatNonBid.of("secondSeat", singletonList(NonBid.of("2", BidRejectionReason.NO_BID))))));

        final CompositeRule<Object, Object> target = CompositeRule.of(asList(firstRule, secondRule));

        // when
        final RuleResult<Object> result = target.process(VALUE, new Object());

        // then
        final Tags expectedTags = TagsImpl.of(
                asList(ActivityImpl.of("firstActivity", "success", emptyList()),
                        ActivityImpl.of("secondActivity", "success", emptyList())));

        List<SeatNonBid> expectedNonBids = List.of(
                SeatNonBid.of("firstSeat", singletonList(NonBid.of("1", BidRejectionReason.NO_BID))),
                SeatNonBid.of("secondSeat", singletonList(NonBid.of("2", BidRejectionReason.NO_BID))));

        assertThat(result).isEqualTo(RuleResult.of(UpdateResult.updated(VALUE), expectedTags, expectedNonBids));

        verify(firstRule).process(eq(VALUE), any());
        verify(secondRule).process(eq(VALUE), any());
    }
}
