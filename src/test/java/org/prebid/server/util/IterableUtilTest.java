package org.prebid.server.util;

import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class IterableUtilTest {

    @Test
    public void unionShouldReturnProperUnion() {
        // given
        final Iterable<Integer> iterable1 = asList(1, 2);
        final Iterable<Integer> iterable2 = singleton(3);

        // when
        final Iterable<Integer> result = IterableUtil.union(iterable1, iterable2);

        // then
        assertThat(result).containsExactly(1, 2, 3);
    }
}
