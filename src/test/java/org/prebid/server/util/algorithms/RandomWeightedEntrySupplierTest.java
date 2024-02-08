package org.prebid.server.util.algorithms;

import org.junit.Test;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class RandomWeightedEntrySupplierTest {

    @Test
    public void getShouldReturnExpectedEntry() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomWeightedEntrySupplier<>(Function.identity());

        // when
        final int result = entrySupplier.get(asList(0, 1));

        //then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void getShouldCorrectlyHandleAllZerosCase() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomWeightedEntrySupplier<>(ignored -> 0);

        // when
        final int result = entrySupplier.get(singleton(1));

        //then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void getShouldCorrectlyHandleNegativeWeights() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomWeightedEntrySupplier<>(number -> -number);

        // when
        final int result = entrySupplier.get(singleton(1));

        //then
        assertThat(result).isEqualTo(1);
    }
}
