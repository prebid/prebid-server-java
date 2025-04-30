package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountRuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.ModelGroupConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.SchemaFunctionConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.CompositeRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.WeightedRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTreeFactory;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedEntry;
import org.prebid.server.hooks.modules.rule.engine.core.util.WeightedList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

public class RequestRuleParser {

    private final ObjectMapper mapper;
    private final RandomGenerator randomGenerator;

    public RequestRuleParser(ObjectMapper mapper, RandomGenerator randomGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.randomGenerator = Objects.requireNonNull(randomGenerator);
    }

    public Map<Stage, Rule<BidRequest>> parse(ObjectNode accountConfig) {
        final AccountConfig parsedConfig;
        try {
            parsedConfig = mapper.treeToValue(accountConfig, AccountConfig.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        return parsedConfig.getRuleSets().stream()
                .collect(Collectors.groupingBy(RuleSetConfig::getStage,
                        Collectors.mapping(
                                ruleSetConfig -> toRule(ruleSetConfig.getModelGroups()),
                                Collectors.toList())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> CompositeRule.of(entry.getValue())));
    }

    private Rule<BidRequest> toRule(List<ModelGroupConfig> modelGroupConfigs) {
        final List<WeightedEntry<Rule<BidRequest>>> weightedRules = modelGroupConfigs.stream()
                .map(config -> WeightedEntry.of(config.getWeight(), toRule(config)))
                .toList();

        return new WeightedRule<>(randomGenerator, new WeightedList<>(weightedRules));
    }

    private static Rule<BidRequest> toRule(ModelGroupConfig config) {
        final Schema<RequestPayload> schema = parseSchema(config.getSchema());

        final List<RuleConfig<BidRequest>> rules = config.getRules().stream()
                .map(RequestRuleParser::toRuleConfig)
                .toList();
        final RuleTree<RuleConfig<BidRequest>> ruleTree = RuleTreeFactory.buildTree(rules);

        return new MatchingRequestRule(schema, ruleTree);
    }

    private static Schema<RequestPayload> parseSchema(List<SchemaFunctionConfig> schema) {
        final Set<String> names = schema.stream().map(SchemaFunctionConfig::getFunction).collect(Collectors.toSet());

        final List<SchemaFunctionHolder<RequestPayload>> schemaFunctions = schema.stream()
                .map(config -> SchemaFunctionHolder.of(
                        RequestSchema.schemaFunctionByName(config.getFunction()), config.getArgs()))
                .toList();

        return Schema.of(names, schemaFunctions);
    }

    private static RuleConfig<BidRequest> toRuleConfig(AccountRuleConfig ruleConfig) {
        final String ruleFired = String.join("|", ruleConfig.getConditions());
        final List<RuleAction<BidRequest>> actions = ruleConfig.getResults().stream()
                .map(config -> RuleAction.of(
                        RequestSchema.resultFunctionByName(config.getFunction()), config.getArgs()))
                .toList();

        return RuleConfig.of(ruleFired, actions);
    }
}
