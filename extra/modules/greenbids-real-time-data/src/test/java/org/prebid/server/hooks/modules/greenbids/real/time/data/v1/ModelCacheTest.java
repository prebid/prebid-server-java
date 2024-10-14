package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import com.google.cloud.storage.Blob;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunnerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private Storage storage;

    private Vertx vertx;

    @Mock
    private Bucket bucket;

    @Mock
    private Blob blob;

    @Mock
    private OnnxModelRunner onnxModelRunner;

    @Mock
    private OnnxModelRunnerFactory onnxModelRunnerFactory;

    @Mock
    private ModelCache target;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        target = new ModelCache(
                storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx, onnxModelRunnerFactory);
    }

    @Test
    public void getShouldReturnModelFromCacheWhenPresent() {
        // given
        String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(onnxModelRunner);

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(onnxModelRunner);
        verify(cache).getIfPresent(eq(cacheKey));
    }

    @Test
    public void getShouldSkipFetchingWhenFetchingInProgress() throws NoSuchFieldException, IllegalAccessException {
        // given
        String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        // Create a spy of the ModelCache class
        ModelCache spyModelCache = spy(target);

        // Mock the cache to simulate that the model is not present
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Spy the isFetching AtomicBoolean behavior
        AtomicBoolean mockFetchingState = mock(AtomicBoolean.class);

        // Mock fetching state for 2 calls
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);

        // Use reflection to set the private field 'isFetching' in the spy accessible
        Field isFetchingField = ModelCache.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        isFetchingField.set(spyModelCache, mockFetchingState);

        // when
        Future<OnnxModelRunner> result = spyModelCache.get(ONNX_MODEL_PATH, PBUUID);

        System.out.println(
                "firstCall.cause().getMessage(): " + result.cause().getMessage() + "\n" +
                        "firstCall.succeeded(): " + result.succeeded()
        );

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
        lenient().when(blob.getContent()).thenReturn(bytes);
        lenient().when(onnxModelRunnerFactory.create(bytes)).thenReturn(onnxModelRunner);

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar: " + ar + "\n" +
                            "   ar.result(): " + ar.result() + "\n" +
                            "   cache: " + cache
            );

            assertThat(ar.succeeded()).isTrue();
            assertThat(ar.result()).isEqualTo(onnxModelRunner);
            verify(cache).put(eq(cacheKey), eq(onnxModelRunner));
        });
    }

    @Test
    public void getShouldThrowExceptionWhenStorageFails() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        // Mock that the model is not in cache
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Simulate an error when accessing the storage bucket
        lenient().when(storage.get(GCS_BUCKET_NAME)).thenThrow(new StorageException(500, "Storage Error"));

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar: " + ar + "\n" +
                            "   ar.failed(): " + ar.failed() + "\n" +
                            "   ar.cause(): " + ar.cause() + "\n" +
                            "   ar.cause().getMessage(): " + ar.cause().getMessage() + "\n" +
                            "   ar.result(): " + ar.result()
            );

            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Error accessing GCS artefact for model");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenOnnxModelFails() throws OrtException {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        final byte[] bytes = new byte[]{1, 2, 3};

        // Mock that the model is not in cache
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Simulate an error when accessing the storage bucket
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);;
        lenient().when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        lenient().when(blob.getContent()).thenReturn(bytes);
        lenient().when(onnxModelRunnerFactory.create(bytes)).thenThrow(new OrtException("Failed to convert blob to ONNX model"));

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar: " + ar + "\n" +
                            "   ar.failed(): " + ar.failed() + "\n" +
                            "   ar.cause(): " + ar.cause() + "\n" +
                            "   ar.cause().getMessage(): " + ar.cause().getMessage() + "\n" +
                            "   ar.result(): " + ar.result()
            );


            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Failed to convert blob to ONNX model");
        });
    }

    @Test
    public void getShouldThrowExceptionWhenBucketNotFound() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        // Mock that the model is not in cache
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Simulate an error when accessing the storage bucket
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);;
        lenient().when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        lenient().when(blob.getContent()).thenThrow(new PreBidException("Bucket not found"));

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar: " + ar + "\n" +
                            "   ar.failed(): " + ar.failed() + "\n" +
                            "   ar.cause(): " + ar.cause() + "\n" +
                            "   ar.cause().getMessage(): " + ar.cause().getMessage() + "\n" +
                            "   ar.result(): " + ar.result()
            );

            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Bucket not found");
        });
    }

}
