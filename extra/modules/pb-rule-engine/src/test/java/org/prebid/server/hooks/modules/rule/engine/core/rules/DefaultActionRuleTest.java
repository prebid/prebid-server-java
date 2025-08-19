package org.prebid.server.hooks.modules.rule.engine.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class DefaultActionRuleTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void processShouldAccumulateResultFromAllRuleActions() {
        // given
        final Object VALUE = new Object();
        final Object CONTEXT = new Object();

        final ObjectNode firstConfig = mapper.createObjectNode().set("config", TextNode.valueOf("test"));
        final ResultFunction<Object, Object> firstFunction =
                (ResultFunction<Object, Object>) mock(ResultFunction.class);
        given(firstFunction.apply(any())).willAnswer(invocationOnMock -> RuleResult.of(
                ((ResultFunctionArguments<Object, Object>) invocationOnMock.getArgument(0)).getOperand(),
                RuleAction.UPDATE,
                TagsImpl.of(singletonList(ActivityImpl.of("firstActivity", "success", emptyList()))),
                singletonList(SeatNonBid.of("firstSeat", singletonList(NonBid.of("1", BidRejectionReason.NO_BID))))));

        final ObjectNode secondConfig = mapper.createObjectNode().set("config", TextNode.valueOf("anotherTest"));
        final ResultFunction<Object, Object> secondFunction =
                (ResultFunction<Object, Object>) mock(ResultFunction.class);
        given(secondFunction.apply(any())).willAnswer(invocationOnMock -> RuleResult.of(
                ((ResultFunctionArguments<Object, Object>) invocationOnMock.getArgument(0)).getOperand(),
                RuleAction.UPDATE,
                TagsImpl.of(singletonList(ActivityImpl.of("secondActivity", "success", emptyList()))),
                singletonList(SeatNonBid.of("secondSeat", singletonList(NonBid.of("2", BidRejectionReason.NO_BID))))));

        final List<ResultFunctionHolder<Object, Object>> actions = List.of(
                ResultFunctionHolder.of("firstFunction", firstFunction, firstConfig),
                ResultFunctionHolder.of("secondFunction", secondFunction, secondConfig));

        final DefaultActionRule<Object, Object> target = new DefaultActionRule<>(
                actions, "analyticsKey", "modelVersion");

        // when
        final RuleResult<Object> result = target.process(VALUE, CONTEXT);

        // then
        final Tags expectedTags = TagsImpl.of(
                asList(ActivityImpl.of("firstActivity", "success", emptyList()),
                        ActivityImpl.of("secondActivity", "success", emptyList())));

        List<SeatNonBid> expectedNonBids = List.of(
                SeatNonBid.of("firstSeat", singletonList(NonBid.of("1", BidRejectionReason.NO_BID))),
                SeatNonBid.of("secondSeat", singletonList(NonBid.of("2", BidRejectionReason.NO_BID))));

        assertThat(result).isEqualTo(RuleResult.of(VALUE, RuleAction.UPDATE, expectedTags, expectedNonBids));
    }
}
