package org.prebid.server.hooks.modules.rule.engine.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountRuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ModelGroupConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ResultFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.SchemaFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.AlternativeActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.DefaultActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.MatchingRuleFactory;
import org.prebid.server.hooks.modules.rule.engine.core.rules.NoOpRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RandomWeightedRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTreeFactory;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedEntry;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class StageConfigParserTest {

    private StageConfigParser<Object, Object> target;

    @Mock(strictness = LENIENT)
    private RandomGenerator randomGenerator;

    @Mock(strictness = LENIENT)
    private StageSpecification<Object, Object> stageSpecification;

    @Mock(strictness = LENIENT)
    private MatchingRuleFactory<Object, Object> matchingRuleFactory;

    @Mock(strictness = LENIENT)
    private SchemaFunction<Object, Object> schemaFunction;

    @Mock(strictness = LENIENT)
    private ResultFunction<Object, Object> resultFunction;

    @Mock(strictness = LENIENT)
    private Rule<Object, Object> matchingRule;

    @Mock(strictness = LENIENT)
    private RuleTreeFactory ruleTreeFactory;

    @BeforeEach
    public void setUp() {
        target = new StageConfigParser<>(
                randomGenerator, Stage.processed_auction_request, stageSpecification, matchingRuleFactory);
    }

    @Test
    public void parseShouldReturnNoOpRuleWhenSubrulesForStageAreAbsent() {
        // when and then
        assertThat(target.parse(AccountConfig.builder().build())).isEqualTo(NoOpRule.create());
    }

    @Test
    public void parseShouldReturnNoOpRuleWhenEnabledSubrulesForStageAreAbsent() {
        // given
        final AccountConfig accountConfig = AccountConfig.builder()
                .ruleSets(Collections.singletonList(
                        RuleSetConfig.builder()
                                .enabled(false)
                                .stage(Stage.processed_auction_request)
                                .build()))
                .build();

        // when and then
        assertThat(target.parse(accountConfig)).isEqualTo(NoOpRule.create());
    }

    @Test
    public void parseShouldCombineModelGroupRulesUnderSameRuleSetIntoRandomWeightedRule() {
        // given
        final ModelGroupConfig firstModelGroupConfig = ModelGroupConfig.builder()
                .weight(1)
                .analyticsKey("analyticsKey1")
                .version("version1")
                .schema(List.of(SchemaFunctionConfig.of("function1", null)))
                .rules(List.of(AccountRuleConfig.of(List.of("condition1"),
                        List.of(ResultFunctionConfig.of("function2", null)))))
                .build();

        final ModelGroupConfig secondModelGroupConfig = ModelGroupConfig.builder()
                .weight(2)
                .analyticsKey("analyticsKey2")
                .version("version2")
                .schema(List.of(SchemaFunctionConfig.of("function1", null)))
                .rules(List.of(AccountRuleConfig.of(List.of("condition1"),
                        List.of(ResultFunctionConfig.of("function2", null)))))
                .build();

        final List<ModelGroupConfig> modelGroupConfigs = Arrays.asList(firstModelGroupConfig, secondModelGroupConfig);

        given(stageSpecification.schemaFunctionByName("function1")).willReturn(schemaFunction);
        given(stageSpecification.resultFunctionByName("function2")).willReturn(resultFunction);
        given(matchingRuleFactory.create(any(), any(), any(), any())).willReturn(matchingRule);

        final AccountConfig accountConfig = givenAccountConfig(modelGroupConfigs);

        // when and then
        final RandomWeightedRule<Object, Object> weightedRule = RandomWeightedRule.of(
                randomGenerator,
                new WeightedList<>(
                        List.of(WeightedEntry.of(1, matchingRule), WeightedEntry.of(2, matchingRule))));

        assertThat(target.parse(accountConfig)).isEqualTo(
                CompositeRule.of(Collections.singletonList(weightedRule)));
    }

    @Test
    public void parseShouldCombineMatchingRuleWithDefaultUnderSameModelGroup() {
        // given
        final ModelGroupConfig modelGroupConfig = ModelGroupConfig.builder()
                .weight(1)
                .analyticsKey("analyticsKey")
                .version("version")
                .schema(List.of(SchemaFunctionConfig.of("function1", null)))
                .defaultAction(List.of(ResultFunctionConfig.of("function3", null)))
                .rules(List.of(AccountRuleConfig.of(List.of("condition1"),
                        List.of(ResultFunctionConfig.of("function2", null)))))
                .build();

        given(stageSpecification.schemaFunctionByName("function1")).willReturn(schemaFunction);
        given(stageSpecification.resultFunctionByName("function2")).willReturn(resultFunction);

        final ResultFunction<Object, Object> secondResultFunction = mock(ResultFunction.class);
        given(stageSpecification.resultFunctionByName("function3")).willReturn(secondResultFunction);

        given(matchingRuleFactory.create(any(), any(), any(), any())).willReturn(matchingRule);

        final AccountConfig accountConfig = givenAccountConfig(modelGroupConfig);

        // when and then
        final DefaultActionRule<Object, Object> defaultRule = new DefaultActionRule<>(
                Collections.singletonList(ResultFunctionHolder.of("function3", secondResultFunction, null)),
                "analyticsKey",
                "version");

        final AlternativeActionRule<Object, Object> alternativeRule = AlternativeActionRule.of(
                matchingRule, defaultRule);

        final RandomWeightedRule<Object, Object> weightedRule = RandomWeightedRule.of(
                randomGenerator, new WeightedList<>(List.of(WeightedEntry.of(1, alternativeRule))));

        assertThat(target.parse(accountConfig)).isEqualTo(
                CompositeRule.of(Collections.singletonList(weightedRule)));
    }

    @Test
    public void parseShouldBuildRuleTreeAndCreateAppropriateMatchingRule() {
        // given
        final ModelGroupConfig modelGroupConfig = ModelGroupConfig.builder()
                .weight(1)
                .analyticsKey("analyticsKey")
                .version("version")
                .schema(List.of(SchemaFunctionConfig.of("function1", null)))
                .rules(List.of(AccountRuleConfig.of(List.of("condition1"),
                        List.of(ResultFunctionConfig.of("function2", null)))))
                .build();

        given(stageSpecification.schemaFunctionByName("function1")).willReturn(schemaFunction);
        given(stageSpecification.resultFunctionByName("function2")).willReturn(resultFunction);

        final Schema<Object, Object> schema = Schema.of(
                Collections.singletonList(SchemaFunctionHolder.of("function1", schemaFunction, null)));

        given(matchingRuleFactory.create(eq(schema), any(), eq("analyticsKey"), eq("version")))
                .willReturn(matchingRule);

        final AccountConfig accountConfig = givenAccountConfig(modelGroupConfig);

        // when and then
        final RandomWeightedRule<Object, Object> weightedRule = RandomWeightedRule.of(
                randomGenerator, new WeightedList<>(List.of(WeightedEntry.of(1, matchingRule))));

        assertThat(target.parse(accountConfig)).isEqualTo(
                CompositeRule.of(Collections.singletonList(weightedRule)));
    }

    private static AccountConfig givenAccountConfig(ModelGroupConfig modelGroupConfig) {
        return givenAccountConfig(Collections.singletonList(modelGroupConfig));
    }

    private static AccountConfig givenAccountConfig(List<ModelGroupConfig> modelGroupConfigs) {
        return AccountConfig.builder()
                .ruleSets(Collections.singletonList(
                        RuleSetConfig.builder()
                                .stage(Stage.processed_auction_request)
                                .modelGroups(modelGroupConfigs)
                                .build()))
                .build();
    }
}
