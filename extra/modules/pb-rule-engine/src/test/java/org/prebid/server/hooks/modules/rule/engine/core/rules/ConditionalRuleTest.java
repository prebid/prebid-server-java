package org.prebid.server.hooks.modules.rule.engine.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.LookupResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class ConditionalRuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ANALYTICS_KEY = "analyticsKey";
    private static final String MODEL_VERSION = "modelVersion";

    private ConditionalRule<Object, Object> target;

    @Mock(strictness = LENIENT)
    private Schema<Object, Object> schema;

    @Mock(strictness = LENIENT)
    private RuleTree<RuleConfig<Object, Object>> ruleTree;

    @Mock(strictness = LENIENT)
    private SchemaFunction<Object, Object> firstSchemaFunction;
    @Mock(strictness = LENIENT)
    private SchemaFunction<Object, Object> secondSchemaFunction;
    @Mock(strictness = LENIENT)
    private ResultFunction<Object, Object> firstResultFunction;
    @Mock(strictness = LENIENT)
    private ResultFunction<Object, Object> secondResultFunction;

    @BeforeEach
    public void setUp() {
        target = new ConditionalRule<>(schema, ruleTree, ANALYTICS_KEY, MODEL_VERSION);
    }

    @Test
    public void processShouldCorrectlyProcessData() {
        // given
        final Object value = new Object();
        final Object context = new Object();

        // two schema functions
        final ObjectNode firstSchemaFunctionConfig = MAPPER.createObjectNode();
        final ObjectNode secondSchemaFunctionConfig = MAPPER.createObjectNode();
        final String firstSchemaFunctionName = "firstFunction";
        final String secondSchemaFunctionName = "secondFunction";
        final String firstSchemaFunctionOutput = "firstSchemaOutput";
        final String secondSchemaFunctionOutput = "secondSchemaOutput";

        given(schema.getFunctions()).willReturn(List.of(
                SchemaFunctionHolder.of(firstSchemaFunctionName, firstSchemaFunction, firstSchemaFunctionConfig),
                SchemaFunctionHolder.of(secondSchemaFunctionName, secondSchemaFunction, secondSchemaFunctionConfig)));

        given(firstSchemaFunction.extract(eq(SchemaFunctionArguments.of(value, firstSchemaFunctionConfig, context))))
                .willReturn(firstSchemaFunctionOutput);
        given(secondSchemaFunction.extract(eq(SchemaFunctionArguments.of(value, secondSchemaFunctionConfig, context))))
                .willReturn(secondSchemaFunctionOutput);

        // two result functions
        final String firstRuleActionName = "firstRuleAction";
        final String secondRuleActionName = "secondRuleAction";
        final ObjectNode firstResultFunctionConfig = MAPPER.createObjectNode();
        final ObjectNode secondResultFunctionConfig = MAPPER.createObjectNode();
        final List<ResultFunctionHolder<Object, Object>> resultFunctionHolders = List.of(
                ResultFunctionHolder.of(firstRuleActionName, firstResultFunction, firstResultFunctionConfig),
                ResultFunctionHolder.of(secondRuleActionName, secondResultFunction, secondResultFunctionConfig));

        final RuleConfig<Object, Object> ruleConfig = RuleConfig.of("ruleCondition", resultFunctionHolders);

        // tree that matches values based on schema functions outputs
        final String firstDimensionMatch = "firstMatch";
        final String secondDimensionMatch = "secondMatch";
        given(ruleTree.lookup(eq(List.of(firstSchemaFunctionOutput, secondSchemaFunctionOutput))))
                .willReturn(LookupResult.of(ruleConfig, List.of(firstDimensionMatch, secondDimensionMatch)));

        // infrastructure arguments passed to result functions
        final InfrastructureArguments<Object> infrastructureArguments = InfrastructureArguments.builder()
                .context(context)
                .schemaFunctionResults(
                        Map.of(firstSchemaFunctionName, firstSchemaFunctionOutput,
                                secondSchemaFunctionName, secondSchemaFunctionOutput))
                .schemaFunctionMatches(
                        Map.of(firstSchemaFunctionName, firstDimensionMatch,
                                secondSchemaFunctionName, secondDimensionMatch))
                .ruleFired(ruleConfig.getCondition())
                .analyticsKey(ANALYTICS_KEY)
                .modelVersion(MODEL_VERSION)
                .build();

        // result of first result function processing
        final Object firstResultFunctionUpdatedValue = new Object();
        final Tags firstTags = TagsImpl.of(
                Collections.singletonList(
                        ActivityImpl.of("firstActivity", "status", Collections.emptyList())));
        final List<SeatNonBid> firstSeatNonBids = Collections.singletonList(
                SeatNonBid.of(
                        "seatA",
                        Collections.singletonList(NonBid.of("impIdA", BidRejectionReason.NO_BID))));

        final RuleResult<Object> firstResultFunctionOutput = RuleResult.of(
                firstResultFunctionUpdatedValue,
                RuleAction.UPDATE,
                firstTags,
                firstSeatNonBids);

        final ResultFunctionArguments<Object, Object> firstResultFunctionArgs = ResultFunctionArguments.of(
                value, firstResultFunctionConfig, infrastructureArguments);

        given(firstResultFunction.apply(eq(firstResultFunctionArgs))).willReturn(firstResultFunctionOutput);

        // result of second result function processing
        final Object secondResultFunctionUpdatedValue = new Object();
        final Tags secondTags = TagsImpl.of(
                Collections.singletonList(
                        ActivityImpl.of("secondActivity", "status", Collections.emptyList())));
        final List<SeatNonBid> secondSeatNonBids = Collections.singletonList(
                SeatNonBid.of(
                        "seatB",
                        Collections.singletonList(NonBid.of("impIdB", BidRejectionReason.NO_BID))));

        final RuleResult<Object> secondResultFunctionOutput = RuleResult.of(
                secondResultFunctionUpdatedValue,
                RuleAction.UPDATE,
                secondTags,
                secondSeatNonBids);

        final ResultFunctionArguments<Object, Object> secondResultFunctionArgs = ResultFunctionArguments.of(
                firstResultFunctionOutput.getValue(),
                secondResultFunctionConfig,
                infrastructureArguments);

        given(secondResultFunction.apply(eq(secondResultFunctionArgs))).willReturn(secondResultFunctionOutput);

        // when
        final RuleResult<Object> result = target.process(value, context);

        // then
        assertThat(result).isEqualTo(
                RuleResult.of(
                        secondResultFunctionOutput.getValue(),
                        RuleAction.UPDATE,
                        TagsImpl.of(ListUtil.union(firstTags.activities(), secondTags.activities())),
                        ListUtil.union(firstSeatNonBids, secondSeatNonBids)));
    }
}
