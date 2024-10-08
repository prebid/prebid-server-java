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

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private Vertx vertx;

    @Mock
    private Bucket bucket;

    @Mock
    private Blob blob;

    @Mock
    private OnnxModelRunner onnxModelRunner;

    @Mock
    private ModelCache target;

    @BeforeEach
    public void setUp() {
        //cache = Mockito.mock(Cache.class);
        //storage = Mockito.mock(Storage.class);
        //vertx = Mockito.mock(Vertx.class);
        //blob = Mockito.mock(Blob.class);
        //onnxModelRunner = Mockito.mock(OnnxModelRunner.class);

        target = new ModelCache(storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx);
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

        //ModelCache targetWithFetching = new ModelCache(
        //        storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx);
        // isFetching.set(true);

        // Create a spy of the ModelCache class
        ModelCache spyModelCache = spy(new ModelCache(storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx));

        // Mock the cache to simulate that the model is not present
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Mock the vertx executeBlocking to simulate fetching process
        /*
        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            try {
                // Simulate the callable being executed successfully
                Object result = callable.call();
                return Future.succeededFuture(result);
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });
         */

        // Spy the isFetching AtomicBoolean behavior
        AtomicBoolean mockFetchingState = mock(AtomicBoolean.class);

        // Mock fetching state for 2 calls
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);

        // Use reflection to set the private field 'isFetching' in the spy

        /*
        ModelCache modelCacheWithMockedFetching = new ModelCache(
                storage, GCS_BUCKET_NAME, cache, MODEL_CACHE_KEY_PREFIX, vertx) {
            protected AtomicBoolean getIsFetching() {
                return mockFetchingState;
            }
        };
         */

        // Use reflection to set the private field 'isFetching' in the spy accessible
        Field isFetchingField = ModelCache.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        isFetchingField.set(spyModelCache, mockFetchingState);

        // when
        Future<OnnxModelRunner> firstCall = spyModelCache.get(ONNX_MODEL_PATH, PBUUID);

        System.out.println(
                "firstCall.cause().getMessage(): " + firstCall.cause().getMessage() + "\n" +
                        "firstCall.succeeded(): " + firstCall.succeeded()
        );

        // then
        assertThat(firstCall.failed()).isTrue();
        assertThat(firstCall.cause().getMessage()).isEqualTo(
                "ModelRunner fetching in progress. Skip current request");
        //verify(vertx).executeBlocking(any(Callable.class));
    }

    @Test
    public void getShouldFetchModelWhenNotInCache() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        // final String onnxModelPath = "models_pbuid=" + PBUUID + ".onnx";

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        // when(vertx.executeBlocking(any(Callable.class)))
        //        .thenReturn(Future.succeededFuture(blob));

        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            try {
                Object result = callable.call();
                return Future.succeededFuture(result);
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);
        System.out.println(
                "future.cause().getMessage(): " + future.cause().getMessage() + "\n" +
                        "future.succeeded(): " + future.succeeded()
        );

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("ModelRunner is fetched. Skip current request");
        verify(vertx).executeBlocking(any(Callable.class));
    }

    @Test
    public void getShouldThrowExceptionWhenStorageFails() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;

        // Mock that the model is not in cache
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);

        // Simulate an error when accessing the storage bucket
        when(storage.get(GCS_BUCKET_NAME)).thenThrow(new StorageException(500, "Storage Error"));

        // Mock vertx.executeBlocking to simulate the behavior of exception being thrown in getBlob

        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            try {
                // The callable should throw an exception when called
                Object result = callable.call();
                return Future.succeededFuture(result);
            } catch (Exception e) {
                // Return a failed future when an exception occurs
                return Future.failedFuture(e);
            }
        });

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);
        /*
        Exception exception = assertThrows(PreBidException.class, () -> {
            // Call the method and expect the PreBidException to be thrown
            Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);
            // Force the future to resolve, triggering the exception
            future.result();
        });
        //Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);
         */

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar.failed(): " + ar.failed() + "\n" +
                            "   ar.cause(): " + ar.cause() + "\n" +
                            "   ar.cause().getMessage(): " + ar.cause().getMessage()
            );

            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Error accessing GCS artefact for model");
        });

        //assertThat(exception.getMessage()).contains("Error accessing GCS artefact for model");
    }

    /*
    @Test
    public void getShouldThrowExceptionIfOnnxModelFails() {
        // given
        final String cacheKey = MODEL_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(ONNX_MODEL_PATH)).thenReturn(blob);
        when(blob.getContent()).thenThrow(new PreBidException("Failed to convert blob to ONNX model"));

        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            try {
                // The callable should throw an exception when called
                Object result = callable.call();
                return Future.succeededFuture(result);
            } catch (Exception e) {
                // Return a failed future when an exception occurs
                return Future.failedFuture(e);
            }
        });

        // when
        Future<OnnxModelRunner> future = target.get(ONNX_MODEL_PATH, PBUUID);

        // then
        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar.failed(): " + ar.failed() + "\n" +
                            "   ar.cause(): " + ar.cause() + "\n" +
                            "   ar.cause().getMessage(): " + ar.cause().getMessage()
            );

            assertThat(ar.cause()).isInstanceOf(PreBidException.class);
            assertThat(ar.cause().getMessage()).contains("Failed to convert blob to ONNX model");
        });
    }
     */
}
