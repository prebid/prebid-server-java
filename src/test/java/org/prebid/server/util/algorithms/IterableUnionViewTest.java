package org.prebid.server.util.algorithms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class IterableUnionViewTest {

    @Test
    public void unionViewShouldActProperly() {
        // given
        final List<Integer> iterable1 = new ArrayList<>(asList(1, 2));
        final Iterable<Integer> iterable2 = singleton(3);

        // when
        final Iterable<Integer> result = new IterablesUnionView<>(iterable1, iterable2);

        // then
        assertThat(result).containsExactly(1, 2, 3);

        // when
        iterable1.add(1, 4);

        // then
        assertThat(result).containsExactly(1, 4, 2, 3);
    }
}
