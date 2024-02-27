package org.prebid.server.util.algorithms;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.Test;
import org.prebid.server.util.IterableUtil;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class CartesianProductIteratorTest {

    @Test
    public void iteratorShouldCorrectlyHandleEmptyCollection() {
        // when
        final Iterator<List<Integer>> result = new CartesianProductIterator<>(Collections.emptyList());

        // then
        assertThat(IteratorUtils.isEmpty(result)).isTrue();
    }

    @Test
    public void iteratorShouldBeEmptyIfAnyIterableEmpty() {
        // given
        final List<Iterable<Integer>> iterables = asList(
                asList(1, 11),
                singleton(2),
                Collections.emptyList(),
                asList(3, 33, 333));

        // when
        final Iterator<List<Integer>> result = new CartesianProductIterator<>(iterables);

        // then
        assertThat(IteratorUtils.isEmpty(result)).isTrue();
    }

    @Test
    public void iteratorShouldReturnExpectedResultWhenOnlySingleValueWerePassed() {
        // given
        final List<Iterable<Integer>> iterables = Collections.singletonList(singleton(2));

        // when
        final Iterator<List<Integer>> result = new CartesianProductIterator<>(iterables);

        // then
        assertThat(IterableUtil.iterable(result)).containsExactly(singletonList(2));
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    public void iteratorShouldReturnExpectedResult() {
        // given
        final List<Iterable<Integer>> iterables = asList(
                asList(1, 11),
                singleton(2),
                asList(3, 33, 333));

        // when
        final Iterator<List<Integer>> result = new CartesianProductIterator<>(iterables);

        // then
        assertThat(IterableUtil.iterable(result))
                .containsExactly(
                        asList(1, 2, 3),
                        asList(1, 2, 33),
                        asList(1, 2, 333),
                        asList(11, 2, 3),
                        asList(11, 2, 33),
                        asList(11, 2, 333));
        assertThat(result.hasNext()).isFalse();
    }
}
