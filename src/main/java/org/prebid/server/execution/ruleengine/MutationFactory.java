package org.prebid.server.execution.ruleengine;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.execution.ruleengine.extractors.ArgumentExtractor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MutationFactory<T> {

    public Mutation<T> buildMutation(List<ArgumentExtractor<T, ?>> extractors, Map<String, Mutation<T>> rules) {
        if (extractors.isEmpty()) {
            return input -> input;
        }

        final List<MutationSubrule<T>> subrules = rules.entrySet().stream()
                .map(entry -> Pair.of(
                        List.of(StringUtils.defaultString(entry.getKey()).split("\\|")),
                        entry.getValue()))
                .map(entry -> new MutationSubrule<>(entry.getKey(), entry.getValue()))
                .toList();

        return parseConditionalMutation(extractors, subrules);
    }

    private Mutation<T> parseConditionalMutation(List<ArgumentExtractor<T, ?>> argumentExtractors,
                                                 List<MutationSubrule<T>> mutationSubrules) {

        final ArgumentExtractor<T, Object> argumentExtractor =
                (ArgumentExtractor<T, Object>) argumentExtractors.getFirst();

        if (argumentExtractors.size() == 1) {
            final Map<Object, Mutation<T>> parsedSubrules = mutationSubrules.stream()
                    // todo: add filter for *
                    .collect(Collectors.toMap(
                            subrule -> argumentExtractor.extract(subrule.argumentMatchers().getFirst()),
                            MutationSubrule::mutation));

            return new ConditionalMutation<>(parsedSubrules, argumentExtractor, input -> input);
        }

        // a | b | c | d
        // a | e | 1 | 2
        // a | * | * | *
        final Map<Object, List<MutationSubrule<T>>> subrules =
                mutationSubrules.stream()
                        // todo: add filter for *
                        .collect(Collectors.groupingBy(
                                subrule -> argumentExtractor.extract(subrule.argumentMatchers().getFirst())));

        final Map<Object, Mutation<T>> parsedSubrules = subrules.entrySet().stream()
                .map(entry -> Pair.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(MutationSubrule::tail)
                                .toList()))

                .map(entry -> Pair.of(
                        entry.getKey(),
                        parseConditionalMutation(tail(argumentExtractors), entry.getValue())))

                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        return new ConditionalMutation<>(parsedSubrules, argumentExtractor, input -> input);
    }

    private static <R> List<R> tail(List<R> list) {
        return list.subList(1, list.size());
    }

    private record MutationSubrule<T>(List<String> argumentMatchers, Mutation<T> mutation) {

        public MutationSubrule<T> tail() {
            return new MutationSubrule<>(MutationFactory.tail(argumentMatchers), mutation);
        }
    }
}
