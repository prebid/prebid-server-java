package org.prebid.server.hooks.modules.id5.userid.v1.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValuesFilterTest {

    @Test
    void shouldAllowAllWhenValuesNull() {
        // given
        final ValuesFilter<String> filter = new ValuesFilter<>();
        filter.setValues(null);
        filter.setExclude(false); // default include mode

        // expect
        assertThat(filter.isValueAllowed("anything")).isTrue();
        assertThat(filter.isValueAllowed(null)).isTrue();
    }

    @Test
    void shouldAllowAllWhenValuesEmpty() {
        // given
        final ValuesFilter<String> filter = new ValuesFilter<>();
        filter.setValues(Set.of());
        filter.setExclude(false);

        // expect
        assertThat(filter.isValueAllowed("x")).isTrue();
        assertThat(filter.isValueAllowed("y")).isTrue();
    }

    @Test
    void shouldWhitelistAllowOnlyListedWhenExcludeFalse() {
        // given
        final ValuesFilter<String> filter = new ValuesFilter<>();
        filter.setExclude(false); // allowlist semantics
        filter.setValues(Set.of("a", "b"));

        // expect
        assertThat(filter.isValueAllowed("a")).isTrue();
        assertThat(filter.isValueAllowed("b")).isTrue();
        assertThat(filter.isValueAllowed("c")).isFalse();
        assertThat(filter.isValueAllowed(null)).isFalse();
    }

    @Test
    void shouldBlacklistRejectListedWhenExcludeTrue() {
        // given
        final ValuesFilter<String> filter = new ValuesFilter<>();
        filter.setExclude(true); // blocklist semantics
        filter.setValues(Set.of("blocked", "forbidden"));

        // expect
        assertThat(filter.isValueAllowed("blocked")).isFalse();
        assertThat(filter.isValueAllowed("forbidden")).isFalse();
        assertThat(filter.isValueAllowed("other")).isTrue();
        assertThat(filter.isValueAllowed(null)).isFalse();
    }
}
