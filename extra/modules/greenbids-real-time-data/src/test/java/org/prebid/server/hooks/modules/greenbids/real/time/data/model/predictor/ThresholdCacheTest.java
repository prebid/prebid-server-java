package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholdsFactory;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ThresholdCacheTest {

    private static final String GCS_BUCKET_NAME = "test_bucket";
    private static final String THRESHOLD_CACHE_KEY_PREFIX = "onnxModelRunner_";
    private static final String PBUUID = "test-pbuid";
    private static final String THRESHOLDS_PATH = "thresholds.json";

    @Mock
    private Cache<String, ThrottlingThresholds> cache;

    @Mock(strictness = LENIENT)
    private Storage storage;

    @Mock(strictness = LENIENT)
    private Bucket bucket;

    @Mock(strictness = LENIENT)
    private Blob blob;

    @Mock
    private ThrottlingThresholds throttlingThresholds;

    @Mock(strictness = LENIENT)
    private ThrottlingThresholdsFactory throttlingThresholdsFactory;

    private Vertx vertx;

    private ObjectMapper mapper;

    private ThresholdCache target;

    @BeforeEach
    public void setUp() {
        mapper = ObjectMapperProvider.mapper();
        vertx = Vertx.vertx();
        target = new ThresholdCache(
                storage,
                GCS_BUCKET_NAME,
                mapper,
                cache,
                THRESHOLD_CACHE_KEY_PREFIX,
                vertx,
                throttlingThresholdsFactory);
    }

    @Test
    public void getShouldReturnThresholdsFromCacheWhenPresent() {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(throttlingThresholds);

        // when
        final Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(throttlingThresholds);
        verify(cache).getIfPresent(eq(cacheKey));
    }

    @Test
    public void getShouldSkipFetchingWhenFetchingInProgress() throws NoSuchFieldException, IllegalAccessException {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;

        final ThresholdCache spyThresholdCache = spy(target);
        final AtomicBoolean mockFetchingState = mock(AtomicBoolean.class);

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);

        final Field isFetchingField = ThresholdCache.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        isFetchingField.set(spyThresholdCache, mockFetchingState);

        // when
        final Future<ThrottlingThresholds> result = spyThresholdCache.get(THRESHOLDS_PATH, PBUUID);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo(
                "ThrottlingThresholds fetching in progress. Skip current request");
    }

    @Test
    public void getShouldFetchThresholdsWhenNotInCache() throws IOException {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        final String jsonContent = "test_json_content";
        final byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(THRESHOLDS_PATH)).thenReturn(blob);
        when(blob.getContent()).thenReturn(bytes);
        when(throttlingThresholdsFactory.create(bytes, mapper)).thenReturn(throttlingThresholds);

        // when
        final Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.succeeded()).isTrue();
            assertThat(ar.result()).isEqualTo(throttlingThresholds);
            verify(cache).put(eq(cacheKey), eq(throttlingThresholds));
        });
    }

    @Test
    public void getShouldThrowExceptionWhenStorageFails() {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenThrow(new StorageException(500, "Storage Error"));

        // when
        final Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Error accessing GCS artefact for threshold");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenLoadingJsonFails() throws IOException {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        final String jsonContent = "test_json_content";
        final byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(THRESHOLDS_PATH)).thenReturn(blob);
        when(blob.getContent()).thenReturn(bytes);
        when(throttlingThresholdsFactory.create(bytes, mapper)).thenThrow(
                new IOException("Failed to load throttling thresholds json"));

        // when
        final Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Failed to load throttling thresholds json");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenBucketNotFound() {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(THRESHOLDS_PATH)).thenReturn(blob);
        when(blob.getContent()).thenThrow(new PreBidException("Bucket not found"));

        // when
        final Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Bucket not found");
        });
    }
}
