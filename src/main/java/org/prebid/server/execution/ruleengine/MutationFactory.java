package org.prebid.server.execution.ruleengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.execution.ruleengine.extractors.ArgumentExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MutationFactory<T> {

    private final Map<String, ArgumentExtractor<T, ?>> argumentExtractors;
    private final Function<JsonNode, Mutation<T>> mutationParser;

    public MutationFactory(Map<String, ArgumentExtractor<T, ?>> argumentExtractors,
                           Function<JsonNode, Mutation<T>> mutationParser) {

        this.argumentExtractors = new HashMap<>(argumentExtractors);
        this.mutationParser = Objects.requireNonNull(mutationParser);
    }

    public Mutation<T> parse(List<String> argumentSchema, Map<String, JsonNode> mutationRules) {
        if (argumentSchema.isEmpty()) {
            return input -> input;
        }

        if (!argumentSchema.stream().allMatch(argumentExtractors::containsKey)) {
            throw new IllegalArgumentException("Invalid arguments schema");
        }

        final List<MutationSubrule<T>> subrules = mutationRules.entrySet().stream()
                .map(entry -> Pair.of(
                        List.of(StringUtils.defaultString(entry.getKey()).split("\\|")),
                        mutationParser.apply(entry.getValue())))
                .map(entry -> new MutationSubrule<>(entry.getKey(), entry.getValue()))
                .toList();

        return parseConditionalMutation(argumentSchema, subrules);
    }

    private Mutation<T> parseConditionalMutation(List<String> argumentSchema,
                                                 List<MutationSubrule<T>> mutationSubrules) {

        final ArgumentExtractor<T, Object> argumentExtractor =
                (ArgumentExtractor<T, Object>) argumentExtractors.get(argumentSchema.getFirst());

        if (argumentSchema.size() == 1) {
            final Map<Object, Mutation<T>> parsedSubrules = mutationSubrules.stream()
                    .collect(Collectors.toMap(
                            subrule -> argumentExtractor.extract(subrule.argumentMatchers().getFirst()),
                            MutationSubrule::mutation,
                            (left, right) -> left,
                            HashMap::new));

            return new ConditionalMutation<>(parsedSubrules, argumentExtractor, input -> input);
        }

        // a | b | c | d
        // a | e | 1 | 2
        // a | * | * | *
        final Map<Object, List<MutationSubrule<T>>> subrules =
                mutationSubrules.stream()
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
                        parseConditionalMutation(tail(argumentSchema), entry.getValue())))

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
