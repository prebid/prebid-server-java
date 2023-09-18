package org.prebid.server.metric;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResettingCounterTest {

    @Test
    public void getCountShouldReset() {
        // given
        final ResettingCounter counter = new ResettingCounter();

        // when
        counter.inc();
        counter.inc();
        final long count1 = counter.getCount();
        counter.inc();
        final long count2 = counter.getCount();

        // then
        assertThat(count1).isEqualTo(2);
        assertThat(count2).isEqualTo(1);
    }
}
