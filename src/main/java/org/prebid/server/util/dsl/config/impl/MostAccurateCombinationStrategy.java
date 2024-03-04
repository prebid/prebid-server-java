package org.prebid.server.util.dsl.config.impl;

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.prebid.server.util.algorithms.CartesianProductIterator;
import org.prebid.server.util.dsl.config.PrebidConfigMatchingStrategy;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;
import org.prebid.server.util.dsl.config.PrebidConfigSchema;
import org.prebid.server.util.dsl.config.PrebidConfigSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Priority order for four column rule sets:
 * <pre>
 * _|_|_|_
 * _|_|_|*
 * _|_|*|_
 * _|*|_|_
 * *|_|_|_
 * _|_|*|*
 * _|*|_|*
 * _|*|*|_
 * *|_|_|*
 * *|_|*|_
 * *|*|_|_
 * _|*|*|*
 * *|_|*|*
 * *|*|_|*
 * *|*|*|_
 * *|*|*|*
 * </pre>
 */
public class MostAccurateCombinationStrategy implements PrebidConfigMatchingStrategy {

    @Override
    public String match(PrebidConfigSource source, PrebidConfigParameters parameters) {
        final Set<String> configuredRules = toSet(source.rules());

        final Iterator<String> iterator = new RuleIterator(source, parameters.get());
        while (iterator.hasNext()) {
            final String generatedRule = iterator.next();
            if (configuredRules.contains(generatedRule)) {
                return generatedRule;
            }
        }
        return null;
    }

    static class RuleIterator implements Iterator<String> {

        private final PrebidConfigSchema schema;

        private final List<String> wildcard;
        private final List<Iterable<String>> parametersValues;
        private final List<Integer> generatedWildcardsIndices;
        private final int initialWildcardsNumber;

        private int currentWildcardsNumber;
        private Iterator<int[]> wildcardsCombinationsIterator;
        private Iterator<List<String>> parametersCartesianProductIterator;

        RuleIterator(PrebidConfigSchema schema, Iterable<PrebidConfigParameter> parameters) {
            this.schema = schema;

            wildcard = Collections.singletonList(schema.wildcard());
            parametersValues = extractParametersValues(parameters, wildcard);
            generatedWildcardsIndices = !parametersValues.isEmpty()
                    ? generateWildcardsIndices(parameters)
                    : Collections.emptyList();
            initialWildcardsNumber = !parametersValues.isEmpty()
                    ? parametersValues.size() - generatedWildcardsIndices.size()
                    : Integer.MAX_VALUE;

            currentWildcardsNumber = initialWildcardsNumber;
            wildcardsCombinationsIterator = IteratorUtils.emptyIterator();
            parametersCartesianProductIterator = new CartesianProductIterator<>(parametersValues);
        }

        @Override
        public boolean hasNext() {
            return currentWildcardsNumber <= parametersValues.size();
        }

        @Override
        public String next() {
            final String rule = buildRule(parametersCartesianProductIterator.next());

            if (!parametersCartesianProductIterator.hasNext()) {
                if (!wildcardsCombinationsIterator.hasNext()) {
                    tryResetWildcardsCombinationsIterator();
                }

                tryResetParametersCartesianProductIterator();
            }

            return rule;
        }

        private String buildRule(List<String> conditions) {
            return StringUtils.join(conditions, schema.separator());
        }

        private void tryResetWildcardsCombinationsIterator() {
            currentWildcardsNumber++;
            if (hasNext()) {
                wildcardsCombinationsIterator = CombinatoricsUtils.combinationsIterator(
                        parametersValues.size() - initialWildcardsNumber,
                        currentWildcardsNumber - initialWildcardsNumber);
            }
        }

        private void tryResetParametersCartesianProductIterator() {
            if (hasNext()) {
                final List<Iterable<String>> sets = parametersValuesWithAddedWildcards();
                parametersCartesianProductIterator = new CartesianProductIterator<>(sets);
            }
        }

        private List<Iterable<String>> parametersValuesWithAddedWildcards() {
            final List<Iterable<String>> newParametersValues = new ArrayList<>(parametersValues);

            final int shift = generatedWildcardsIndices.size() - 1;
            for (int wildcardIndex : wildcardsCombinationsIterator.next()) {
                final int index = generatedWildcardsIndices.get(shift - wildcardIndex);
                newParametersValues.set(index, wildcard);
            }

            return newParametersValues;
        }
    }

    private static List<Iterable<String>> extractParametersValues(Iterable<PrebidConfigParameter> parameters,
                                                                  List<String> wildcard) {

        final List<Iterable<String>> parametersValues = new ArrayList<>();
        for (PrebidConfigParameter parameter : parameters) {
            final Iterable<String> parameterValues = valuesOrWildcard(parameter, wildcard);
            if (IterableUtils.isEmpty(parameterValues)) {
                return Collections.emptyList();
            }

            parametersValues.add(parameterValues);
        }
        return parametersValues;
    }

    private static Iterable<String> valuesOrWildcard(PrebidConfigParameter parameter, Iterable<String> wildcard) {
        return parameter instanceof PrebidConfigParameter.Direct direct
                ? direct.values()
                : wildcard;
    }

    private static List<Integer> generateWildcardsIndices(Iterable<PrebidConfigParameter> parameters) {
        final List<Integer> generatedWildcardsIndices = new ArrayList<>();

        int i = 0;
        for (Iterator<PrebidConfigParameter> it = parameters.iterator(); it.hasNext(); i++) {
            if (it.next() instanceof PrebidConfigParameter.Direct) {
                generatedWildcardsIndices.add(i);
            }
        }

        return generatedWildcardsIndices;
    }

    private static Set<String> toSet(Iterable<String> iterable) {
        return iterable instanceof Set<String> set ? set : fill(new HashSet<>(), iterable);
    }

    private static <E, C extends Collection<E>> C fill(C destination, Iterable<E> source) {
        for (E element : source) {
            destination.add(element);
        }
        return destination;
    }
}
