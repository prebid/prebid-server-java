package org.prebid.server.identity;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NoneIdGeneratorTest {

    @Test
    public void shouldReturnNull() {
        // given
        final NoneIdGenerator generator = new NoneIdGenerator();

        // when
        final String id = generator.generateId();

        // then
        assertThat(id).isNull();
    }
}
