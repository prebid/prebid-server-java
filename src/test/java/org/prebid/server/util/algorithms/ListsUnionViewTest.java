package org.prebid.server.util.algorithms;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ListsUnionViewTest {

    @Test
    public void unionViewShouldActProperly() {
        // given
        final List<Integer> list1 = new ArrayList<>(asList(1, 2));
        final List<Integer> list2 = singletonList(3);

        // when
        final List<Integer> result = new ListsUnionView<>(list1, list2);

        // then
        assertThat(result).containsExactly(1, 2, 3);

        // when
        list1.add(1, 4);

        // then
        assertThat(result).containsExactly(1, 4, 2, 3);
    }
}
