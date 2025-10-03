package org.prebid.server.hooks.modules.rule.engine.core.config;

import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountRuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ModelGroupConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ResultFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.SchemaFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.AlternativeActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ConditionalRuleFactory;
import org.prebid.server.hooks.modules.rule.engine.core.rules.DefaultActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.NoOpRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RandomWeightedRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidMatcherConfiguration;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTreeFactory;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedEntry;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public class StageConfigParser<T, C> {

    private final RandomGenerator randomGenerator;
    private final StageSpecification<T, C> specification;
    private final Stage stage;
    private final ConditionalRuleFactory<T, C> conditionalRuleFactory;

    public StageConfigParser(RandomGenerator randomGenerator,
                             Stage stage,
                             StageSpecification<T, C> specification,
                             ConditionalRuleFactory<T, C> conditionalRuleFactory) {

        this.randomGenerator = Objects.requireNonNull(randomGenerator);
        this.stage = Objects.requireNonNull(stage);
        this.specification = Objects.requireNonNull(specification);
        this.conditionalRuleFactory = Objects.requireNonNull(conditionalRuleFactory);
    }

    public Rule<T, C> parse(AccountConfig config) {
        final List<Rule<T, C>> stageSubrules = config.getRuleSets().stream()
                .filter(ruleSet -> stage.equals(ruleSet.getStage()))
                .filter(RuleSetConfig::isEnabled)
                .map(RuleSetConfig::getModelGroups)
                .map(this::parseModelGroupConfigs)
                .toList();

        return stageSubrules.isEmpty()
                ? NoOpRule.create()
                : CompositeRule.of(stageSubrules);
    }

    private Rule<T, C> parseModelGroupConfigs(List<ModelGroupConfig> modelGroupConfigs) {
        final List<WeightedEntry<Rule<T, C>>> weightedRules = modelGroupConfigs.stream()
                .map(config -> WeightedEntry.of(config.getWeight(), parseModelGroupConfig(config)))
                .toList();

        return RandomWeightedRule.of(randomGenerator, new WeightedList<>(weightedRules));
    }

    private Rule<T, C> parseModelGroupConfig(ModelGroupConfig config) {
        final Rule<T, C> matchingRule = parseMatchingRule(config);
        final Rule<T, C> defaultRule = parseDefaultActionRule(config);

        return combineRules(matchingRule, defaultRule);
    }

    private Rule<T, C> parseMatchingRule(ModelGroupConfig config) {
        final List<SchemaFunctionConfig> schemaConfig = config.getSchema();
        final List<AccountRuleConfig> rulesConfig = config.getRules();

        if (CollectionUtils.isEmpty(schemaConfig) || CollectionUtils.isEmpty(rulesConfig)) {
            return null;
        }

        final Schema<T, C> schema = parseSchema(schemaConfig);

        final List<RuleConfig<T, C>> rules = rulesConfig.stream()
                .map(this::parseRuleConfig)
                .toList();
        final RuleTree<RuleConfig<T, C>> ruleTree = RuleTreeFactory.buildTree(rules);

        if (schemaConfig.size() != ruleTree.getDepth()) {
            throw new InvalidMatcherConfiguration("Schema functions count and rules matchers count mismatch");
        }

        return conditionalRuleFactory.create(schema, ruleTree, config.getAnalyticsKey(), config.getVersion());
    }

    private Schema<T, C> parseSchema(List<SchemaFunctionConfig> schema) {
        final List<SchemaFunctionHolder<T, C>> schemaFunctions = schema.stream()
                .map(config -> SchemaFunctionHolder.of(
                        config.getFunction(),
                        specification.schemaFunctionByName(config.getFunction()),
                        config.getArgs()))
                .toList();

        schemaFunctions.forEach(this::validateFunctionConfig);

        return Schema.of(schemaFunctions);
    }

    private void validateFunctionConfig(SchemaFunctionHolder<T, C> holder) {
        try {
            holder.getSchemaFunction().validateConfig(holder.getConfig());
        } catch (ConfigurationValidationException exception) {
            throw new InvalidMatcherConfiguration(
                    "Function '%s' configuration is invalid: %s".formatted(holder.getName(), exception.getMessage()));
        }
    }

    private RuleConfig<T, C> parseRuleConfig(AccountRuleConfig ruleConfig) {
        final String ruleFired = String.join("|", ruleConfig.getConditions());
        final List<ResultFunctionHolder<T, C>> actions = parseActions(ruleConfig.getResults());

        return RuleConfig.of(ruleFired, actions);
    }

    private Rule<T, C> parseDefaultActionRule(ModelGroupConfig config) {
        final List<ResultFunctionConfig> defaultActionConfig = config.getDefaultAction();

        if (CollectionUtils.isEmpty(config.getDefaultAction())) {
            return null;
        }

        return new DefaultActionRule<>(
                parseActions(defaultActionConfig), config.getAnalyticsKey(), config.getVersion());
    }

    private List<ResultFunctionHolder<T, C>> parseActions(List<ResultFunctionConfig> functionConfigs) {
        final List<ResultFunctionHolder<T, C>> actions = functionConfigs.stream()
                .map(config -> ResultFunctionHolder.of(
                        config.getFunction(),
                        specification.resultFunctionByName(config.getFunction()),
                        config.getArgs()))
                .toList();

        actions.forEach(this::validateActionConfig);

        return actions;
    }

    private void validateActionConfig(ResultFunctionHolder<T, C> action) {
        try {
            action.getFunction().validateConfig(action.getConfig());
        } catch (ConfigurationValidationException exception) {
            throw new InvalidMatcherConfiguration(
                    "Function '%s' configuration is invalid: %s".formatted(action.getName(), exception.getMessage()));
        }
    }

    private Rule<T, C> combineRules(Rule<T, C> left, Rule<T, C> right) {
        if (left == null && right == null) {
            return NoOpRule.create();
        } else if (left != null && right != null) {
            return AlternativeActionRule.of(left, right);
        } else if (left != null) {
            return AlternativeActionRule.of(left, NoOpRule.create());
        }

        return AlternativeActionRule.of(right, NoOpRule.create());
    }
}
