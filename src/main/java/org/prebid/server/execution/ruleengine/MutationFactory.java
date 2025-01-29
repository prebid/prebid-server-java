package org.prebid.server.execution.ruleengine;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.ruleengine.extractors.ArgumentExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MutationFactory<T> {

    private static final String DEFAULT_ARGUMENT_MATCHER = "*";

    private final Mutation<T> identityMutation = input -> input;

    public Mutation<T> buildMutation(List<ArgumentExtractor<T, ?>> extractors, Map<String, Mutation<T>> rules) {
        if (extractors.isEmpty()) {
            return identityMutation;
        }

        return parseConditionalMutation(extractors, toParsingContexts(extractors, rules));
    }

    private List<ParsingContext<T>> toParsingContexts(List<ArgumentExtractor<T, ?>> extractors,
                                                      Map<String, Mutation<T>> rules) {

        final List<ParsingContext<T>> contexts = new ArrayList<>();

        for (Map.Entry<String, Mutation<T>> entry : rules.entrySet()) {
            final List<String> arguments = List.of(StringUtils.defaultString(entry.getKey()).split("\\|"));
            final Mutation<T> mutation = entry.getValue();

            if (extractors.size() != arguments.size()) {
                throw new IllegalArgumentException("Argument count mismatch");
            }

            contexts.add(new ParsingContext<>(arguments, mutation));
        }

        return contexts;
    }

    private Mutation<T> parseConditionalMutation(List<ArgumentExtractor<T, ?>> argumentExtractors,
                                                 List<ParsingContext<T>> parsingContexts) {

        if (argumentExtractors.isEmpty()) {
            if (parsingContexts.size() > 1) {
                throw new IllegalArgumentException("Ambiguous matcher rules");
            }

            return parsingContexts.getFirst().mutation();
        }

        final ArgumentExtractor<T, Object> argumentExtractor =
                (ArgumentExtractor<T, Object>) argumentExtractors.getFirst();
        final List<ArgumentExtractor<T, ?>> nextArgumentExtractors = tail(argumentExtractors);

        final Map<String, List<ParsingContext<T>>> subrules = parsingContexts.stream()
                .collect(Collectors.groupingBy(
                        ParsingContext::argumentMatcher,
                        Collectors.mapping(ParsingContext::next, Collectors.toList())));

        final Mutation<T> defaultAction = Optional.ofNullable(subrules.get(DEFAULT_ARGUMENT_MATCHER))
                .map(subrule -> parseConditionalMutation(nextArgumentExtractors, subrule))
                .orElse(identityMutation);

        subrules.remove(DEFAULT_ARGUMENT_MATCHER);

        final Map<Object, Mutation<T>> parsedSubrules = subrules.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> argumentExtractor.extract(entry.getKey()),
                        entry -> parseConditionalMutation(nextArgumentExtractors, entry.getValue())));

        return new ConditionalMutation<>(parsedSubrules, argumentExtractor, defaultAction);
    }

    private record ParsingContext<T>(List<String> argumentMatchers, Mutation<T> mutation) {

        public ParsingContext<T> next() {
            return new ParsingContext<>(tail(argumentMatchers), mutation);
        }

        public String argumentMatcher() {
            return argumentMatchers.getFirst();
        }
    }

    private static <R> List<R> tail(List<R> list) {
        return list.subList(1, list.size());
    }
}
