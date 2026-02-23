package org.prebid.server.settings.service;

import io.vertx.core.Future;
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
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class S3PeriodicRefreshServiceTest extends VertxTest {

    private static final String BUCKET = "bucket";
    private static final String STORED_REQ_DIR = "stored-req";
    private static final String STORED_IMP_DIR = "stored-imp";

    @Mock(strictness = LENIENT)
    private S3AsyncClient s3AsyncClient;

    @Mock
    private CacheNotificationListener<String> cacheNotificationListener;

    @Mock
    private Clock clock;

    @Mock
    private Metrics metrics;

    private Vertx vertx;

    @BeforeEach
    public void setUp() {
        vertx = spy(Vertx.vertx());

        given(s3AsyncClient.listObjects(eq(ListObjectsRequest.builder()
                .bucket(BUCKET)
                .prefix(STORED_REQ_DIR)
                .build())))
                .willReturn(listObjectResponse(STORED_REQ_DIR + "/id1.json"));
        given(s3AsyncClient.listObjects(eq(ListObjectsRequest.builder()
                .bucket(BUCKET)
                .prefix(STORED_IMP_DIR)
                .build())))
                .willReturn(listObjectResponse(STORED_IMP_DIR + "/id2.json"));

        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(STORED_REQ_DIR + "/id1.json")
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(getObjectResponse("value1"));
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(STORED_IMP_DIR + "/id2.json")
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(getObjectResponse("value2"));

        given(clock.millis()).willReturn(100L, 500L);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void initializeShouldCallSaveWithExpectedParameters(VertxTestContext context) {
        // when and then
        createAndInitService(100).onComplete(context.succeeding(ignored -> {
            verify(cacheNotificationListener, atLeast(1))
                    .save(singletonMap("id1", "value1"), singletonMap("id2", "value2"));
            verify(metrics, atLeast(1)).updateSettingsCacheRefreshTime(
                    eq(MetricName.stored_request), eq(MetricName.initialize), eq(400L));

            context.completeNow();
        }));
    }

    @Test
    public void initializeShouldNotCreatePeriodicTaskIfRefreshPeriodIsNegative(VertxTestContext context) {
        // when and then
        createAndInitService(-1).onComplete(context.succeeding(unused -> {
            verify(vertx, never()).setPeriodic(anyLong(), any());

            context.completeNow();
        }));
    }

    @Test
    public void initializeShouldUpdateMetricsOnError(VertxTestContext context) {
        // given
        given(s3AsyncClient.listObjects(any(ListObjectsRequest.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("Failed")));

        // when
        createAndInitService(100).onComplete(context.failing(ignored -> {
            verify(metrics, atLeast(1)).updateSettingsCacheRefreshTime(
                    eq(MetricName.stored_request), eq(MetricName.initialize), eq(400L));
            verify(metrics, atLeast(1)).updateSettingsCacheRefreshErrorMetric(
                    eq(MetricName.stored_request), eq(MetricName.initialize));

            context.completeNow();
        }));
    }

    private CompletableFuture<ListObjectsResponse> listObjectResponse(String key) {
        return CompletableFuture.completedFuture(
                ListObjectsResponse
                        .builder()
                        .contents(singletonList(S3Object.builder().key(key).build()))
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
                cacheNotificationListener,
                MetricName.stored_request,
                clock,
                metrics,
                vertx);

        final Promise<Void> init = Promise.promise();
        s3PeriodicRefreshService.initialize(init);
        return init.future();
    }
}
