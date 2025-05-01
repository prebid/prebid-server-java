package org.prebid.server.hooks.modules.rule.engine.core.config;

import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountRuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ModelGroupConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.SchemaFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.AlternativeActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.DefaultActionRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.MatchingRuleFactory;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.WeightedRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTreeFactory;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedEntry;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public class StageConfigParser<SCHEMA_PAYLOAD, RULE_PAYLOAD> {

    private final RandomGenerator randomGenerator;
    private final StageSpecification<SCHEMA_PAYLOAD, RULE_PAYLOAD> specification;
    private final Stage stage;
    private final MatchingRuleFactory<SCHEMA_PAYLOAD, RULE_PAYLOAD> matchingRuleFactory;

    public StageConfigParser(RandomGenerator randomGenerator,
                             Stage stage,
                             StageSpecification<SCHEMA_PAYLOAD, RULE_PAYLOAD> specification,
                             MatchingRuleFactory<SCHEMA_PAYLOAD, RULE_PAYLOAD> matchingRuleFactory) {

        this.randomGenerator = Objects.requireNonNull(randomGenerator);
        this.stage = Objects.requireNonNull(stage);
        this.specification = Objects.requireNonNull(specification);
        this.matchingRuleFactory = Objects.requireNonNull(matchingRuleFactory);
    }

    public Rule<RULE_PAYLOAD> parse(AccountConfig config) {
        final List<Rule<RULE_PAYLOAD>> stageSubrules = config.getRuleSets().stream()
                .filter(ruleSet -> stage.equals(ruleSet.getStage()))
                .map(RuleSetConfig::getModelGroups)
                .map(this::parseModelGroupConfigs)
                .toList();

        return CompositeRule.of(stageSubrules);
    }

    private Rule<RULE_PAYLOAD> parseModelGroupConfigs(List<ModelGroupConfig> modelGroupConfigs) {
        final List<WeightedEntry<Rule<RULE_PAYLOAD>>> weightedRules = modelGroupConfigs.stream()
                .map(config -> WeightedEntry.of(config.getWeight(), parseModelGroupConfig(config)))
                .toList();

        return new WeightedRule<>(randomGenerator, new WeightedList<>(weightedRules));
    }

    private Rule<RULE_PAYLOAD> parseModelGroupConfig(ModelGroupConfig config) {
        final String analyticsKey = config.getAnalyticsKey();
        final String version = config.getVersion();

        final Schema<SCHEMA_PAYLOAD> schema = parseSchema(config.getSchema());

        final List<RuleConfig<RULE_PAYLOAD>> rules = config.getRules().stream()
                .map(this::parseRuleConfig)
                .toList();
        final RuleTree<RuleConfig<RULE_PAYLOAD>> ruleTree = RuleTreeFactory.buildTree(rules);

        final Rule<RULE_PAYLOAD> matchingRule = matchingRuleFactory.create(schema, ruleTree, analyticsKey, version);
        final Rule<RULE_PAYLOAD> defaultRule = new DefaultActionRule<>(
                parseActions(config.getDefaultAction()), analyticsKey, version);

        return new AlternativeActionRule<>(matchingRule, defaultRule);
    }

    private Schema<SCHEMA_PAYLOAD> parseSchema(List<SchemaFunctionConfig> schema) {
        final List<SchemaFunctionHolder<SCHEMA_PAYLOAD>> schemaFunctions = schema.stream()
                .map(config -> SchemaFunctionHolder.of(
                        config.getFunction(),
                        specification.schemaFunctionByName(config.getFunction()), config.getArgs()))
                .toList();

        return Schema.of(schemaFunctions);
    }

    private RuleConfig<RULE_PAYLOAD> parseRuleConfig(AccountRuleConfig ruleConfig) {
        final String ruleFired = String.join("|", ruleConfig.getConditions());
        final List<RuleAction<RULE_PAYLOAD>> actions = parseActions(ruleConfig.getResults());

        return RuleConfig.of(ruleFired, actions);
    }

    private List<RuleAction<RULE_PAYLOAD>> parseActions(List<RuleFunctionConfig> functionConfigs) {
        return functionConfigs.stream()
                .map(config -> RuleAction.of(
                        specification.resultFunctionByName(config.getFunction()), config.getArgs()))
                .toList();
    }
}
