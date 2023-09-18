package org.prebid.server.identity;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UUIDIdGeneratorTest {

    @Test
    public void shouldGenerateUUID() {
        // given
        final UUIDIdGenerator generator = new UUIDIdGenerator();

        // when
        final String id = generator.generateId();

        // then
        assertThat(id).satisfies(value -> assertThat(UUID.fromString(value)).isInstanceOf(UUID.class));
    }
}
