package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RuleFactory<T, R> {

    public <U> Rule<T, R, U> buildRule(List<ArgumentExtractor<U>> argumentExtractors,
                                       Map<String, Function<T, R>> rules) {

        return new Rule<>(new RuleTree<>(parseRuleNode(toParsingContexts(rules))), argumentExtractors);
    }

    private List<ParsingContext<T, R>> toParsingContexts(Map<String, Function<T, R>> rules) {
        final List<ParsingContext<T, R>> contexts = new ArrayList<>();

        for (Map.Entry<String, Function<T, R>> entry : rules.entrySet()) {
            final List<String> arguments = List.of(StringUtils.defaultString(entry.getKey()).split("\\|"));
            final Function<T, R> action = entry.getValue();


            contexts.add(new ParsingContext<>(arguments, action));
        }

        return contexts;
    }

    private RuleNode<T, R> parseRuleNode(List<ParsingContext<T, R>> parsingContexts) {
        if (parsingContexts.size() == 1 && parsingContexts.getFirst().argumentMatchers().isEmpty()) {
            return new RuleNode.LeafNode<>(parsingContexts.getFirst().action);
        }

        final Map<String, List<ParsingContext<T, R>>> subrules = parsingContexts.stream()
                .collect(Collectors.groupingBy(
                        ParsingContext::argumentMatcher,
                        Collectors.mapping(ParsingContext::next, Collectors.toList())));

        final Map<String, RuleNode<T, R>> parsedSubrules = subrules.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> parseRuleNode(entry.getValue())));

        return new RuleNode.IntermediateNode<>(parsedSubrules);
    }

    private record ParsingContext<T, R>(List<String> argumentMatchers, Function<T, R> action) {

        public ParsingContext<T, R> next() {
            return new ParsingContext<>(tail(argumentMatchers), action);
        }

        public String argumentMatcher() {
            return argumentMatchers.getFirst();
        }
    }

    private static <R> List<R> tail(List<R> list) {
        return list.subList(1, list.size());
    }
}
