package org.prebid.server.hooks.modules.optable.targeting.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CachingKeyTest {

    @Test
    public void shouldBuildCachingUrlEncodedKey() {
        // given
        final Query query = Query.of("query?String", "&attributes");
        final CachingKey cachingKey = CachingKey.of("tenant", "origin", query, List.of("8.8.8.8"));

        // when
        final String key = cachingKey.toEncodedString();

        // then
        Assertions.assertThat(key).isEqualTo("tenant:origin:8.8.8.8:query%3FString");
    }

    @Test
    public void shouldBuildCachingKey() {
        // given
        final Query query = Query.of("query?String", "&attributes");
        final CachingKey cachingKey = CachingKey.of("tenant", "origin", query, List.of("8.8.8.8"));

        // when
        final String key = cachingKey.toString();

        // then
        Assertions.assertThat(key).isEqualTo("tenant:origin:8.8.8.8:query?String");
    }

    @Test
    public void shouldPutNoneIfNoIpAddress() {
        // given
        final Query query = Query.of("query?String", "&attributes");
        final CachingKey cachingKey = CachingKey.of("tenant", "origin", query, null);

        // when
        final String key = cachingKey.toString();

        // then
        Assertions.assertThat(key).isEqualTo("tenant:origin:none:query?String");
    }
}
