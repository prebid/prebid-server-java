package org.prebid.server.hooks.modules.rule.engine.core.config;

import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountRuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ModelGroupConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.SchemaFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
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
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

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
                .map(this::toRule)
                .toList();

        return CompositeRule.of(stageSubrules);
    }

    private Rule<RULE_PAYLOAD> toRule(List<ModelGroupConfig> modelGroupConfigs) {
        final List<WeightedEntry<Rule<RULE_PAYLOAD>>> weightedRules = modelGroupConfigs.stream()
                .map(config -> WeightedEntry.of(config.getWeight(), toRule(config)))
                .toList();

        return new WeightedRule<>(randomGenerator, new WeightedList<>(weightedRules));
    }

    private Rule<RULE_PAYLOAD> toRule(ModelGroupConfig config) {
        final Schema<SCHEMA_PAYLOAD> schema = parseSchema(config.getSchema());

        final List<RuleConfig<RULE_PAYLOAD>> rules = config.getRules().stream()
                .map(this::toRuleConfig)
                .toList();
        final RuleTree<RuleConfig<RULE_PAYLOAD>> ruleTree = RuleTreeFactory.buildTree(rules);

        return matchingRuleFactory.create(schema, ruleTree);
    }

    private Schema<SCHEMA_PAYLOAD> parseSchema(List<SchemaFunctionConfig> schema) {
        final Set<String> names = schema.stream().map(SchemaFunctionConfig::getFunction).collect(Collectors.toSet());

        final List<SchemaFunctionHolder<SCHEMA_PAYLOAD>> schemaFunctions = schema.stream()
                .map(config -> SchemaFunctionHolder.of(
                        specification.schemaFunctionByName(config.getFunction()), config.getArgs()))
                .toList();

        return Schema.of(names, schemaFunctions);
    }

    private RuleConfig<RULE_PAYLOAD> toRuleConfig(AccountRuleConfig ruleConfig) {
        final String ruleFired = String.join("|", ruleConfig.getConditions());
        final List<RuleAction<RULE_PAYLOAD>> actions = ruleConfig.getResults().stream()
                .map(config -> RuleAction.of(
                        specification.resultFunctionByName(config.getFunction()), config.getArgs()))
                .toList();

        return RuleConfig.of(ruleFired, actions);
    }
}
