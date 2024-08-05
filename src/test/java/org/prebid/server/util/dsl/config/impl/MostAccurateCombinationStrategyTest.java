package org.prebid.server.util.dsl.config.impl;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.Test;
import org.prebid.server.util.IterableUtil;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;
import org.prebid.server.util.dsl.config.PrebidConfigSchema;
import org.prebid.server.util.dsl.config.PrebidConfigSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class MostAccurateCombinationStrategyTest {

    @Test
    public void matchShouldPickRulesInExactPriority() {
        // given
        final Set<String> rules = new HashSet<>(rules());
        final PrebidConfigSource source = SimpleSource.of("*", "|", rules);
        final PrebidConfigParameters parameters = SimpleParameters.of(asList(
                SimpleDirectParameter.of(singleton("_")),
                SimpleDirectParameter.of(singleton("_")),
                SimpleDirectParameter.of(singleton("_")),
                SimpleDirectParameter.of(singleton("_")),
                SimpleDirectParameter.of(singleton("_"))));

        final MostAccurateCombinationStrategy target = new MostAccurateCombinationStrategy();

        // when and then
        final List<String> rulesByPriority = new ArrayList<>();

        int i = 1 << 5; // just in case of infinite loop
        while (!rules.isEmpty() && i-- > 0) {
            final String rule = target.match(source, parameters);
            assertThat(rule).isNotNull();

            rulesByPriority.add(rule);
            rules.remove(rule);
        }

        assertThat(rulesByPriority).containsExactlyElementsOf(rulesByPriority());
    }

    @Test
    public void iteratorShouldBeEmptyIfParametersEmpty() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("*", "|", null);
        final Iterable<PrebidConfigParameter> parameters = emptyList();

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IteratorUtils.isEmpty(result)).isTrue();
    }

    @Test
    public void iteratorShouldBeEmptyIfAnyParameterEmpty() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("*", "|", null);
        final Iterable<PrebidConfigParameter> parameters = asList(
                SimpleDirectParameter.of(asList("1", "11")),
                SimpleDirectParameter.of(singleton("2")),
                SimpleDirectParameter.of(emptyList()),
                SimpleDirectParameter.of(asList("3", "33", "333")));

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IteratorUtils.isEmpty(result)).isTrue();
    }

    @Test
    public void iteratorShouldReturnExpectedResultWhenOnlySingleValueWerePassed() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("*", "|", null);
        final Iterable<PrebidConfigParameter> parameters = singleton(SimpleDirectParameter.of(singleton("1")));

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IterableUtil.iterable(result)).containsExactly("1", "*");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    public void iteratorShouldReturnExpectedResultWhenOnlyWildcardsWerePassed() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("*", "|", null);
        final Iterable<PrebidConfigParameter> parameters = asList(
                PrebidConfigParameter.wildcard(),
                PrebidConfigParameter.wildcard(),
                PrebidConfigParameter.wildcard(),
                PrebidConfigParameter.wildcard());

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IterableUtil.iterable(result)).containsExactly("*|*|*|*");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    public void iteratorShouldReturnExpectedResult() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("***", "|", null);
        final Iterable<PrebidConfigParameter> parameters = asList(
                SimpleDirectParameter.of(asList("  1", " 11")),
                SimpleDirectParameter.of(singleton("  2")),
                SimpleDirectParameter.of(asList("  3", " 33", "333")));

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IterableUtil.iterable(result)).containsExactly(
                "  1|  2|  3",
                "  1|  2| 33",
                "  1|  2|333",
                " 11|  2|  3",
                " 11|  2| 33",
                " 11|  2|333",
                "  1|  2|***",
                " 11|  2|***",
                "  1|***|  3",
                "  1|***| 33",
                "  1|***|333",
                " 11|***|  3",
                " 11|***| 33",
                " 11|***|333",
                "***|  2|  3",
                "***|  2| 33",
                "***|  2|333",
                "  1|***|***",
                " 11|***|***",
                "***|  2|***",
                "***|***|  3",
                "***|***| 33",
                "***|***|333",
                "***|***|***");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    public void iteratorShouldReturnExpectedResultWhenWildcardsInitiallyPresent() {
        // given
        final PrebidConfigSchema schema = SimpleSource.of("***", "|", null);
        final Iterable<PrebidConfigParameter> parameters = asList(
                PrebidConfigParameter.wildcard(),
                SimpleDirectParameter.of(asList("  1", " 11")),
                PrebidConfigParameter.wildcard(),
                SimpleDirectParameter.of(singleton("  2")),
                PrebidConfigParameter.wildcard(),
                PrebidConfigParameter.wildcard(),
                SimpleDirectParameter.of(asList("  3", " 33", "333")),
                PrebidConfigParameter.wildcard());

        // when
        final Iterator<String> result = new MostAccurateCombinationStrategy.RuleIterator(schema, parameters);

        // then
        assertThat(IterableUtil.iterable(result)).containsExactly(
                "***|  1|***|  2|***|***|  3|***",
                "***|  1|***|  2|***|***| 33|***",
                "***|  1|***|  2|***|***|333|***",
                "***| 11|***|  2|***|***|  3|***",
                "***| 11|***|  2|***|***| 33|***",
                "***| 11|***|  2|***|***|333|***",
                "***|  1|***|  2|***|***|***|***",
                "***| 11|***|  2|***|***|***|***",
                "***|  1|***|***|***|***|  3|***",
                "***|  1|***|***|***|***| 33|***",
                "***|  1|***|***|***|***|333|***",
                "***| 11|***|***|***|***|  3|***",
                "***| 11|***|***|***|***| 33|***",
                "***| 11|***|***|***|***|333|***",
                "***|***|***|  2|***|***|  3|***",
                "***|***|***|  2|***|***| 33|***",
                "***|***|***|  2|***|***|333|***",
                "***|  1|***|***|***|***|***|***",
                "***| 11|***|***|***|***|***|***",
                "***|***|***|  2|***|***|***|***",
                "***|***|***|***|***|***|  3|***",
                "***|***|***|***|***|***| 33|***",
                "***|***|***|***|***|***|333|***",
                "***|***|***|***|***|***|***|***");
        assertThat(result.hasNext()).isFalse();
    }

    private static List<String> rules() {
        return asList(
                "_|_|_|_|_",
                "_|_|_|_|*",
                "_|_|_|*|_",
                "_|_|_|*|*",
                "_|_|*|_|_",
                "_|_|*|_|*",
                "_|_|*|*|_",
                "_|_|*|*|*",
                "_|*|_|_|_",
                "_|*|_|_|*",
                "_|*|_|*|_",
                "_|*|_|*|*",
                "_|*|*|_|_",
                "_|*|*|_|*",
                "_|*|*|*|_",
                "_|*|*|*|*",
                "*|_|_|_|_",
                "*|_|_|_|*",
                "*|_|_|*|_",
                "*|_|_|*|*",
                "*|_|*|_|_",
                "*|_|*|_|*",
                "*|_|*|*|_",
                "*|_|*|*|*",
                "*|*|_|_|_",
                "*|*|_|_|*",
                "*|*|_|*|_",
                "*|*|_|*|*",
                "*|*|*|_|_",
                "*|*|*|_|*",
                "*|*|*|*|_",
                "*|*|*|*|*");
    }

    private static List<String> rulesByPriority() {
        return asList(
                "_|_|_|_|_",
                "_|_|_|_|*",
                "_|_|_|*|_",
                "_|_|*|_|_",
                "_|*|_|_|_",
                "*|_|_|_|_",
                "_|_|_|*|*",
                "_|_|*|_|*",
                "_|_|*|*|_",
                "_|*|_|_|*",
                "_|*|_|*|_",
                "_|*|*|_|_",
                "*|_|_|_|*",
                "*|_|_|*|_",
                "*|_|*|_|_",
                "*|*|_|_|_",
                "_|_|*|*|*",
                "_|*|_|*|*",
                "_|*|*|_|*",
                "_|*|*|*|_",
                "*|_|_|*|*",
                "*|_|*|_|*",
                "*|_|*|*|_",
                "*|*|_|_|*",
                "*|*|_|*|_",
                "*|*|*|_|_",
                "_|*|*|*|*",
                "*|_|*|*|*",
                "*|*|_|*|*",
                "*|*|*|_|*",
                "*|*|*|*|_",
                "*|*|*|*|*");
    }
}
