package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholdsFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.ThresholdCache;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private Storage storage;

    @Mock
    private Bucket bucket;

    @Mock
    private Blob blob;

    @Mock
    private ThrottlingThresholds throttlingThresholds;

    @Mock
    private ThrottlingThresholdsFactory throttlingThresholdsFactory;

    private Vertx vertx;

    private JacksonMapper jacksonMapper;

    private ObjectMapper mapper;

    private ThresholdCache target;

    @BeforeEach
    public void setUp() {
        mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);
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
        String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(throttlingThresholds);

        // when
        Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(throttlingThresholds);
        verify(cache).getIfPresent(eq(cacheKey));
    }

    @Test
    public void getShouldSkipFetchingWhenFetchingInProgress() throws NoSuchFieldException, IllegalAccessException {
        // given
        String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;

        ThresholdCache spyThresholdCache = spy(target);
        AtomicBoolean mockFetchingState = mock(AtomicBoolean.class);

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(mockFetchingState.compareAndSet(false, true)).thenReturn(false);

        Field isFetchingField = ThresholdCache.class.getDeclaredField("isFetching");
        isFetchingField.setAccessible(true);
        isFetchingField.set(spyThresholdCache, mockFetchingState);

        // when
        Future<ThrottlingThresholds> result = spyThresholdCache.get(THRESHOLDS_PATH, PBUUID);

        // then
        System.out.println(
                "firstCall.cause().getMessage(): " + result.cause().getMessage() + "\n" +
                        "firstCall.succeeded(): " + result.succeeded()
        );

        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo(
                "ThrottlingThresholds fetching in progress. Skip current request");
    }

    @Test
    public void getShouldFetchThresholdsWhenNotInCache() throws IOException {
        // given
        final String cacheKey = THRESHOLD_CACHE_KEY_PREFIX + PBUUID;
        final String jsonContent = """
            {
              "thresholds": [
                0.4,
                0.224,
                0.018,
                0.018
              ],
              "tpr": [
                0.8,
                0.95,
                0.99,
                0.9999
              ]
            }
        """;
        final byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);

        /*
        int[] intArray = {
                32, 32, 32, 32, 123, 10, 32, 32, 32, 32, 32, 32, 34, 116, 104, 114, 101, 115, 104, 111, 108, 100, 115, 34, 58,
                32, 91, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46, 52, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46, 50,
                50, 52, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46, 48, 49, 56, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32,
                48, 46, 48, 49, 56, 10, 32, 32, 32, 32, 32, 32, 93, 44, 10, 32, 32, 32, 32, 32, 32, 34, 116, 112, 114, 34,
                58, 32, 91, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46, 56, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46,
                57, 53, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48, 46, 57, 57, 44, 10, 32, 32, 32, 32, 32, 32, 32, 32, 48,
                46, 57, 57, 57, 57, 10, 32, 32, 32, 32, 32, 32, 93, 10, 32, 32, 32, 32, 125, 10
        };
        byte[] bytes = new byte[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            bytes[i] = (byte) intArray[i];
        }
         */
        System.out.println("bytes: " + mapper.readTree(bytes));

        //final JsonNode thresholdsJsonNode = mapper.readTree(bytes);
        //ThrottlingThresholds tempThrottlingThresholds = mapper.treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
        //ThrottlingThresholds expectedThrottlingThresholds = ThrottlingThresholds.of(
        //        List.of(0.4, 0.224, 0.018, 0.018),
        //        List.of(0.8, 0.95, 0.99, 0.9999)
        //);

        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        when(bucket.get(THRESHOLDS_PATH)).thenReturn(blob);
        lenient().when(blob.getContent()).thenReturn(bytes);
        //when(mapper.readTree(bytes)).thenReturn(thresholdsJsonNode);
        //when(mapper.treeToValue(thresholdsJsonNode, ThrottlingThresholds.class)).thenReturn(throttlingThresholds);
        lenient().when(throttlingThresholdsFactory.create(bytes, mapper)).thenReturn(throttlingThresholds);

        // when
        Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);
        System.out.println("future: " + future);

        // then
        /*
        future.onComplete(testContext.succeeding(result -> {
            System.out.println(
                    "future.onComplete: \n" +
                            "   throttlingThresholds: " + throttlingThresholds + "\n"
            );

            assertThat(result).isEqualTo(throttlingThresholds);
            verify(cache).put(eq(cacheKey), eq(throttlingThresholds));
            testContext.completeNow();
        }));
         */

        future.onComplete(ar -> {

            System.out.println(
                    "future.onComplete: \n" +
                            "   ar: " + ar + "\n" +
                            "   ar.result(): " + ar.result() + "\n" +
                            "   cache: " + cache
            );

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
        Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

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
        final String jsonContent = """
            {
              "thresholds": [
                0.4,
                0.224,
                0.018,
                0.018
              ],
              "tpr": [
                0.8,
                0.95,
                0.99,
                0.9999
              ]
            }
        """;
        final byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        when(cache.getIfPresent(eq(cacheKey))).thenReturn(null);
        when(storage.get(GCS_BUCKET_NAME)).thenReturn(bucket);
        lenient().when(bucket.get(THRESHOLDS_PATH)).thenReturn(blob);
        lenient().when(blob.getContent()).thenReturn(bytes);
        lenient().when(throttlingThresholdsFactory.create(bytes, mapper)).thenThrow(
                new IOException("Failed to load throttling thresholds json"));

        // when
        Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

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
        lenient().when(blob.getContent()).thenThrow(new PreBidException("Bucket not found"));

        // when
        Future<ThrottlingThresholds> future = target.get(THRESHOLDS_PATH, PBUUID);

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
