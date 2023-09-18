package org.prebid.server.settings;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.settings.model.StoredItem;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class SettingsCacheTest {

    private SettingsCache settingsCache;

    @Before
    public void setUp() {
        settingsCache = new SettingsCache(10, 10);
    }

    @Test
    public void getRequestCacheShouldReturnEmptyMap() {
        assertThat(settingsCache.getRequestCache()).isEmpty();
    }

    @Test
    public void getImpCacheShouldReturnEmptyMap() {
        assertThat(settingsCache.getImpCache()).isEmpty();
    }

    @Test
    public void saveRequestCacheShouldAddNewRequestsToCache() {
        // when
        settingsCache.saveRequestCache("1001", "reqId1", "reqValue1");

        // then
        assertThat(settingsCache.getRequestCache()).hasSize(1)
                .containsEntry("reqId1", singleton(StoredItem.of("1001", "reqValue1")));
    }

    @Test
    public void saveImpCacheShouldAddNewImpsToCache() {
        // when
        settingsCache.saveImpCache("1001", "impId1", "impValue1");

        // then
        assertThat(settingsCache.getImpCache()).hasSize(1)
                .containsEntry("impId1", singleton(StoredItem.of("1001", "impValue1")));
    }

    @Test
    public void saveShouldAddNewItemsToCache() {
        // when
        settingsCache.save(singletonMap("reqId1", "reqValue1"), singletonMap("impId1", "impValue1"));

        // then
        assertThat(settingsCache.getRequestCache()).hasSize(1)
                .containsEntry("reqId1", singleton(StoredItem.of(null, "reqValue1")));
        assertThat(settingsCache.getImpCache()).hasSize(1)
                .containsEntry("impId1", singleton(StoredItem.of(null, "impValue1")));
    }

    @Test
    public void invalidateShouldRemoveItemsFromCache() {
        // given
        settingsCache.save(singletonMap("reqId1", "reqValue1"), singletonMap("impId1", "impValue1"));
        settingsCache.save(singletonMap("reqId2", "reqValue2"), singletonMap("impId2", "impValue2"));

        // when
        settingsCache.invalidate(singletonList("reqId1"), singletonList("impId1"));

        // then
        assertThat(settingsCache.getRequestCache()).hasSize(1)
                .containsEntry("reqId2", singleton(StoredItem.of(null, "reqValue2")));
        assertThat(settingsCache.getImpCache()).hasSize(1)
                .containsEntry("impId2", singleton(StoredItem.of(null, "impValue2")));
    }
}
