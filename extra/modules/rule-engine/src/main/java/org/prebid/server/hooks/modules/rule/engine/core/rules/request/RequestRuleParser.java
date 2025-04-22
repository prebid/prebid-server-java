package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.RuleSetConfig;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestRuleParser {

    private final ObjectMapper mapper;

    public RequestRuleParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Map<Stage, RequestRule> parse(ObjectNode accountConfig) {
        final AccountConfig parsedConfig;
        try {
            parsedConfig = mapper.treeToValue(accountConfig, AccountConfig.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        return parsedConfig.getRuleSets().stream()
                .map(RequestRuleParser::toRulePerStage)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> CompositeRequestRule.of(entry.getValue())));
    }

    private static Map<Stage, RequestRule> toRulePerStage(RuleSetConfig ruleSetConfig) {
        ruleSetConfig.getStage()
    }
}
