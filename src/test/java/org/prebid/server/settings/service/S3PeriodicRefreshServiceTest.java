package org.prebid.server.settings.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CacheNotificationListener;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class S3PeriodicRefreshServiceTest extends VertxTest {

    private static final String BUCKET = "bucket";
    private static final String STORED_REQ_DIR = "stored-req";
    private static final String STORED_IMP_DIR = "stored-imp";

    @Mock
    private CacheNotificationListener cacheNotificationListener;

    @Mock
    private Vertx vertx;
    private Vertx vertxImpl;

    @Mock(strictness = LENIENT)
    private S3AsyncClient s3AsyncClient;
    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    @Mock
    private Metrics metrics;

    private final Map<String, String> expectedRequests = singletonMap("id1", "value1");
    private final Map<String, String> expectedImps = singletonMap("id2", "value2");

    @BeforeEach
    public void setUp() {
        vertxImpl = Vertx.vertx();
        given(s3AsyncClient.listObjects(any(ListObjectsRequest.class)))
                .willReturn(listObjectResponse(STORED_REQ_DIR + "/id1.json"),
                        listObjectResponse(STORED_IMP_DIR + "/id2.json"));

        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                                ResponseBytes.fromByteArray(
                                        GetObjectResponse.builder().build(),
                                        "value1".getBytes())),
                        CompletableFuture.completedFuture(
                                ResponseBytes.fromByteArray(
                                        GetObjectResponse.builder().build(),
                                        "value2".getBytes())));

        given(vertx.getOrCreateContext()).willReturn(vertxImpl.getOrCreateContext());
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertxImpl.close(context.succeedingThenComplete());
    }

    @Test
    public void shouldCallSaveWithExpectedParameters(VertxTestContext context) {
        // when
        createAndInitService(1000)
                .onSuccess(unused -> {
                    verify(cacheNotificationListener).save(expectedRequests, expectedImps);
                })
                .onComplete(context.succeedingThenComplete());

    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequestsWithParam(VertxTestContext context) {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L, 2L));

        // when
        createAndInitService(1000)
                .onSuccess(unused -> {
                    // then
                    verify(s3AsyncClient, times(6)).listObjects(any(ListObjectsRequest.class));
                    verify(s3AsyncClient, times(6)).getObject(
                            any(GetObjectRequest.class), any(AsyncResponseTransformer.class)
                    );
                })
                .onComplete(context.succeedingThenComplete());

    }

    @Test
    public void initializeShouldMakeOnlyOneInitialRequestIfRefreshPeriodIsNegative(VertxTestContext context) {
        // when
        createAndInitService(-1)
                .onSuccess(unused -> {
                    // then
                    verify(vertx, never()).setPeriodic(anyLong(), any());
                    verify(s3AsyncClient, times(2)).listObjects(any(ListObjectsRequest.class));
                })
                .onComplete(context.succeedingThenComplete());

    }

    @Test
    public void shouldUpdateTimerMetric(VertxTestContext context) {
        // when
        createAndInitService(1000)
                .onSuccess(unused -> {
                    // then
                    verify(metrics).updateSettingsCacheRefreshTime(
                            eq(MetricName.stored_request), eq(MetricName.initialize), anyLong());
                })
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    public void shouldUpdateTimerAndErrorMetric(VertxTestContext context) {
        // given
        given(s3AsyncClient.listObjects(any(ListObjectsRequest.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("Failed")));

        // when
        createAndInitService(1000)
                .onFailure(unused -> {
                    // then
                    verify(metrics).updateSettingsCacheRefreshTime(
                            eq(MetricName.stored_request), eq(MetricName.initialize), anyLong());
                    verify(metrics).updateSettingsCacheRefreshErrorMetric(
                            eq(MetricName.stored_request), eq(MetricName.initialize));
                })
                .onComplete(context.failingThenComplete());

    }

    private CompletableFuture<ListObjectsResponse> listObjectResponse(String... keys) {
        return CompletableFuture.completedFuture(
                ListObjectsResponse
                        .builder()
                        .contents(Arrays.stream(keys).map(key -> S3Object.builder().key(key).build()).toList())
                        .build());
    }

    private CompletableFuture<ResponseBytes<GetObjectResponse>> getObjectResponse(String value) {
        return CompletableFuture.completedFuture(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        value.getBytes()));
    }

    private Future<Void> createAndInitService(long refreshPeriod) {
        final S3PeriodicRefreshService s3PeriodicRefreshService = new S3PeriodicRefreshService(
                s3AsyncClient,
                BUCKET,
                STORED_REQ_DIR,
                STORED_IMP_DIR,
                refreshPeriod,
                MetricName.stored_request,
                cacheNotificationListener,
                vertx,
                metrics,
                clock);
        final Promise<Void> init = Promise.promise();
        s3PeriodicRefreshService.initialize(init);
        return init.future();
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T... objects) {
        return inv -> {
            // invoking handler right away passing mock to it
            for (T obj : objects) {
                ((Handler<T>) inv.getArgument(1)).handle(obj);
            }
            return 0L;
        };
    }

}
