package org.prebid.server.hooks.modules.rule.engine.core.rules.tree;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidMatcherConfiguration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RuleTreeFactory {

    public static <T, C> RuleTree<RuleConfig<T, C>> buildTree(List<RuleConfig<T, C>> rules) {
        final List<ParsingContext<RuleConfig<T, C>>> parsingContexts = toParsingContexts(rules);
        final int depth = getDepth(parsingContexts);

        if (!parsingContexts.stream().allMatch(context -> context.argumentMatchers().size() == depth)) {
            throw new InvalidMatcherConfiguration("Mismatched arguments count");
        }

        return new RuleTree<>(parseRuleNode(parsingContexts), depth);
    }

    private static <T, C> List<ParsingContext<RuleConfig<T, C>>> toParsingContexts(List<RuleConfig<T, C>> rules) {
        return rules.stream()
                .map(rule -> new ParsingContext<>(
                        List.of(StringUtils.defaultString(rule.getCondition()).split("\\|")),
                        rule))
                .toList();
    }

    private static <T> int getDepth(List<ParsingContext<T>> contexts) {
        return contexts.isEmpty() ? 0 : contexts.getFirst().argumentMatchers().size();
    }

    private static <T> RuleNode<T> parseRuleNode(List<ParsingContext<T>> parsingContexts) {
        if (parsingContexts.size() == 1 && parsingContexts.getFirst().argumentMatchers().isEmpty()) {
            return new RuleNode.LeafNode<>(parsingContexts.getFirst().value);
        }

        final Map<String, List<ParsingContext<T>>> subrules = parsingContexts.stream()
                .collect(Collectors.groupingBy(
                        ParsingContext::argumentMatcher,
                        Collectors.mapping(ParsingContext::next, Collectors.toList())));

        final Map<String, RuleNode<T>> parsedSubrules = subrules.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> parseRuleNode(entry.getValue())));

        return new RuleNode.IntermediateNode<>(parsedSubrules);
    }

    private record ParsingContext<T>(List<String> argumentMatchers, T value) {

        public ParsingContext<T> next() {
            return new ParsingContext<>(tail(argumentMatchers), value);
        }

        public String argumentMatcher() {
            return argumentMatchers.getFirst();
        }
    }

    private static <R> List<R> tail(List<R> list) {
        return list.subList(1, list.size());
    }
}
