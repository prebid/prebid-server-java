package org.prebid.server.util.algorithms.random;

import org.junit.Test;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RandomAnyWeightedEntrySupplierTest {

    @Test
    public void getShouldReturnExpectedEntry() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomAnyWeightedEntrySupplier<>(Function.identity());

        // when
        final int result = entrySupplier.get(asList(0, 1));

        //then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void getShouldCorrectlyHandleEmptyCollection() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomAnyWeightedEntrySupplier<>(ignored -> 0);

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entrySupplier.get(emptyList()))
                .withMessage("Empty collection provided.");
    }

    @Test
    public void getShouldCorrectlyHandleAllZerosWeights() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomAnyWeightedEntrySupplier<>(ignored -> 0);

        // when
        final int result = entrySupplier.get(singleton(1));

        //then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void getShouldCorrectlyHandleNegativeWeights() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomAnyWeightedEntrySupplier<>(number -> -number);

        // when
        final int result = entrySupplier.get(singleton(1));

        //then
        assertThat(result).isEqualTo(1);
    }
}
