package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RuleFactory {

    public static RuleTree<ResultFunctionHolder> buildTree(Map<String, ResultFunctionHolder> rules) {
        return new RuleTree<>(parseRuleNode(toParsingContexts(rules)));
    }

    private static <T> List<ParsingContext<T>> toParsingContexts(Map<String, T> rules) {
        final List<ParsingContext<T>> contexts = new ArrayList<>();

        for (Map.Entry<String, T> entry : rules.entrySet()) {
            final List<String> arguments = List.of(StringUtils.defaultString(entry.getKey()).split("\\|"));
            final T value = entry.getValue();


            contexts.add(new ParsingContext<>(arguments, value));
        }

        return contexts;
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
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> parseRuleNode(entry.getValue())));

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
