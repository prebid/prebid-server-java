package org.prebid.server.util.algorithms.random;

import org.junit.Test;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class RandomPositiveWeightedEntrySupplierTest {

    @Test // can flack with a low probability
    public void getShouldReturnExpectedEntry() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomPositiveWeightedEntrySupplier<>(Function.identity());

        // when
        final int result = entrySupplier.get(asList(1, Integer.MAX_VALUE));

        //then
        assertThat(result).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void getShouldCorrectlyHandleEmptyCollection() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomPositiveWeightedEntrySupplier<>(ignored -> 0);

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entrySupplier.get(emptyList()))
                .withMessage("Empty collection provided.");
    }

    @Test
    public void getShouldCorrectlyHandleZeroWeights() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomPositiveWeightedEntrySupplier<>(ignored -> 0);

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entrySupplier.get(singleton(1)))
                .withMessage("Non-positive weight.");
    }

    @Test
    public void getShouldCorrectlyHandleNegativeWeights() {
        // given
        final RandomWeightedEntrySupplier<Integer> entrySupplier =
                new RandomPositiveWeightedEntrySupplier<>(number -> -number);

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entrySupplier.get(singleton(1)))
                .withMessage("Non-positive weight.");
    }
}
