package org.prebid.server.cache.utils;

import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CacheServiceUtilTest extends VertxTest {

    @Test
    public void getCacheEndpointUrlShouldFailOnInvalidCacheServiceUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheServiceUtil.getCacheEndpointUrl("http", "{invalid:host}", "cache"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheServiceUtil.getCacheEndpointUrl("invalid-schema", "example-server:80808", "cache"));
    }

    @Test
    public void getCacheEndpointUrlShouldReturnValidUrl() {
        // when
        final String result = CacheServiceUtil.getCacheEndpointUrl("http", "example.com", "cache").toString();

        // then
        assertThat(result).isEqualTo("http://example.com/cache");
    }

    @Test
    public void getCachedAssetUrlTemplateShouldFailOnInvalidCacheServiceUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheServiceUtil.getCachedAssetUrlTemplate("http", "{invalid:host}", "cache", "qs"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheServiceUtil.getCachedAssetUrlTemplate("invalid-schema", "example-server:80808", "cache", "qs"));
    }

    @Test
    public void getCachedAssetUrlTemplateShouldReturnValidUrl() {
        // when
        final String result = CacheServiceUtil.getCachedAssetUrlTemplate("http", "example.com", "cache", "qs");

        // then
        assertThat(result).isEqualTo("http://example.com/cache?qs");
    }
}
