package org.prebid.server.settings.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.Initializable;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * Service that periodically calls s3 for stored request updates.
 * If refreshRate is negative, then the data will never be refreshed.
 * <p>
 * Fetches all files from the specified folders/prefixes in s3 and downloads all files.
 */
public class S3PeriodicRefreshService implements Initializable {

    private static final String JSON_SUFFIX = ".json";

    private static final Logger logger = LoggerFactory.getLogger(S3PeriodicRefreshService.class);

    private final S3AsyncClient asyncClient;
    private final String bucket;
    private final String storedRequestsDirectory;
    private final String storedImpressionsDirectory;
    private final long refreshPeriod;
    private final CacheNotificationListener cacheNotificationListener;
    private final MetricName cacheType;
    private final Clock clock;
    private final Metrics metrics;
    private final Vertx vertx;

    public S3PeriodicRefreshService(S3AsyncClient asyncClient,
                                    String bucket,
                                    String storedRequestsDirectory,
                                    String storedImpressionsDirectory,
                                    long refreshPeriod,
                                    CacheNotificationListener cacheNotificationListener,
                                    MetricName cacheType,
                                    Clock clock,
                                    Metrics metrics,
                                    Vertx vertx) {

        this.asyncClient = Objects.requireNonNull(asyncClient);
        this.bucket = Objects.requireNonNull(bucket);
        this.storedRequestsDirectory = Objects.requireNonNull(storedRequestsDirectory);
        this.storedImpressionsDirectory = Objects.requireNonNull(storedImpressionsDirectory);
        this.refreshPeriod = refreshPeriod;
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.cacheType = Objects.requireNonNull(cacheType);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics);
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        fetchStoredDataResult(clock.millis(), MetricName.initialize)
                .<Void>mapEmpty()
                .onComplete(initializePromise);

        if (refreshPeriod > 0) {
            logger.info("Starting s3 periodic refresh for " + cacheType + " every " + refreshPeriod + " s");
            vertx.setPeriodic(refreshPeriod, ignored -> fetchStoredDataResult(clock.millis(), MetricName.update));
        }
    }

    private Future<StoredDataResult> fetchStoredDataResult(long startTime, MetricName metricName) {
        return Future.all(
                        getFileContentsForDirectory(storedRequestsDirectory),
                        getFileContentsForDirectory(storedImpressionsDirectory))
                .map(CompositeFuture::<Map<String, String>>list)
                .map(results -> StoredDataResult.of(results.getFirst(), results.get(1), Collections.emptyList()))
                .onSuccess(storedDataResult -> handleResult(storedDataResult, startTime, metricName))
                .onFailure(exception -> handleFailure(exception, startTime, metricName));
    }

    private Future<Map<String, String>> getFileContentsForDirectory(String directory) {
        return listFiles(directory)
                .map(files -> files.stream().map(this::downloadFile).toList())
                .compose(Future::all)
                .map(CompositeFuture::<Tuple2<String, String>>list)
                .map(fileNameToContent -> fileNameToContent.stream()
                        .collect(Collectors.toMap(
                                entry -> stripFileName(directory, entry.getLeft()),
                                Tuple2::getRight)));
    }

    private Future<List<String>> listFiles(String prefix) {
        final ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        return Future.fromCompletionStage(asyncClient.listObjects(listObjectsRequest), vertx.getOrCreateContext())
                .map(response -> response.contents().stream()
                        .map(S3Object::key)
                        .collect(Collectors.toList()));
    }

    private Future<Tuple2<String, String>> downloadFile(String key) {
        final GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

        return Future.fromCompletionStage(
                        asyncClient.getObject(request, AsyncResponseTransformer.toBytes()),
                        vertx.getOrCreateContext())
                .map(content -> Tuple2.of(key, content.asUtf8String()));
    }

    private static String stripFileName(String directory, String name) {
        return name
                .replace(directory + "/", "")
                .replace(JSON_SUFFIX, "");
    }

    private void handleResult(StoredDataResult storedDataResult, long startTime, MetricName refreshType) {
        cacheNotificationListener.save(storedDataResult.getStoredIdToRequest(), storedDataResult.getStoredIdToImp());
        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);
    }

    private void handleFailure(Throwable exception, long startTime, MetricName refreshType) {
        logger.warn("Error occurred while request to s3 refresh service", exception);

        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);
        metrics.updateSettingsCacheRefreshErrorMetric(cacheType, refreshType);
    }
}
