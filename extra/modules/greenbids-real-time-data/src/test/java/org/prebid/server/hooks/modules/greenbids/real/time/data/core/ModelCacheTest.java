package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import ai.onnxruntime.OrtException;
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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ModelCacheTest {

    private static final String GCS_BUCKET_NAME = "test_bucket";
    private static final String MODEL_CACHE_KEY_PREFIX = "onnxModelRunner_";
    private static final String PBUUID = "test-pbuid";
    private static final String ONNX_MODEL_PATH = "model.onnx";

    @Mock
    private Cache<String, OnnxModelRunner> cache;

    @Mock(strictness = LENIENT)
    private Storage storage;

    @Mock(strictness = LENIENT)
    private Bucket bucket;

    @Mock(strictness = LENIENT)
    private Blob blob;

    @Mock
    private OnnxModelRunner onnxModelRunner;

    @Mock(strictness = LENIENT)
    private OnnxModelRunnerFactory onnxModelRunnerFactory;

    @Mock
    private ModelCache target;

    private Vertx vertx;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        target = new ModelCache(
                storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx, onnxModelRunnerFactory);
    }

    @Test
    public void getShouldReturnModelFromCacheWhenPresent() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(onnxModelRunner);

        // when
        final Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(onnxModelRunner);
        verify(cache).getIfPresent(eq(cacheKey));
    }

    @Test
    public void getShouldSkipFetchingWhenFetchingInProgress() throws NoSuchFieldException, IllegalAccessException {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        final ModelCache spyModelCache = spy(target);
        final AtomicBoolean mockFetchingState = mock(AtomicBoolean.class);

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);
        final Field isFetchingField = ModelCache.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        isFetchingField.set(spyModelCache, mockFetchingState);

        // when
        final Future<OnnxModelRunner> result = spyModelCache.get(ONNX_MODEL_PATH, PBUUID);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo(
                "ModelRunner fetching in progress. Skip current request");
    }

    @Test
    public void getShouldFetchModelWhenNotInCache() throws OrtException {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        final byte[] bytes = new byte[]{1, 2, 3};

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        when(blob.getContent()).thenReturn(bytes);
        when(onnxModelRunnerFactory.create(bytes)).thenReturn(onnxModelRunner);

        // when
        final Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.succeeded()).isTrue();
            assertThat(ar.result()).isEqualTo(onnxModelRunner);
            verify(cache).put(eq(cacheKey), eq(onnxModelRunner));
        });
    }

    @Test
    public void getShouldThrowExceptionWhenStorageFails() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenThrow(new StorageException(500, "Storage Error"));

        // when
        final Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Error accessing GCS artefact for model");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenOnnxModelFails() throws OrtException {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        final byte[] bytes = new byte[]{1, 2, 3};

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        when(blob.getContent()).thenReturn(bytes);
        when(onnxModelRunnerFactory.create(bytes)).thenThrow(
                new OrtException("Failed to convert blob to ONNX model"));

        // when
        final Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Failed to convert blob to ONNX model");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenBucketNotFound() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        when(blob.getContent()).thenThrow(new PreBidException("Bucket not found"));

        // when
        final Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {
            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Bucket not found");
        });
    }

}
