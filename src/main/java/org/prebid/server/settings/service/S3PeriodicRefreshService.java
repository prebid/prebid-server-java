package org.prebid.server.settings.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.Initializable;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.concurrent.atomic.AtomicReference;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final String storedImpressionsDirectory;
    private final String storedRequestsDirectory;
    private final long refreshPeriod;
    private final MetricName cacheType;
    private final CacheNotificationListener cacheNotificationListener;
    private final Vertx vertx;
    private final Metrics metrics;
    private final Clock clock;
    private AtomicReference<StoredDataResult> lastResult;

    public S3PeriodicRefreshService(S3AsyncClient asyncClient,
                                    String bucket,
                                    String storedRequestsDirectory,
                                    String storedImpressionsDirectory,
                                    long refreshPeriod,
                                    MetricName cacheType,
                                    CacheNotificationListener cacheNotificationListener,
                                    Vertx vertx,
                                    Metrics metrics,
                                    Clock clock) {

        this.asyncClient = Objects.requireNonNull(asyncClient);
        this.bucket = Objects.requireNonNull(bucket);
        this.storedRequestsDirectory = Objects.requireNonNull(storedRequestsDirectory);
        this.storedImpressionsDirectory = Objects.requireNonNull(storedImpressionsDirectory);
        this.refreshPeriod = refreshPeriod;
        this.cacheType = Objects.requireNonNull(cacheType);
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.vertx = Objects.requireNonNull(vertx);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.lastResult = new AtomicReference<>();
    }

    private static Set<String> getInvalidatedKeys(Map<String, String> newMap, Map<String, String> oldMap) {
        return SetUtils.difference(newMap.keySet(), oldMap.keySet());
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        getAll();
        if (refreshPeriod > 0) {
            vertx.setPeriodic(refreshPeriod, ignored -> refresh());
        }

        initializePromise.tryComplete();
    }

    private void getAll() {
        final long startTime = clock.millis();

        getFileContentsForDirectory(storedRequestsDirectory)
                .compose(storedIdToRequest -> getFileContentsForDirectory(storedImpressionsDirectory)
                        .map(storedIdToImp ->
                                StoredDataResult.of(storedIdToRequest, storedIdToImp, Collections.emptyList())))
                .onSuccess(storedDataResult -> handleResult(storedDataResult, startTime, MetricName.initialize))
                .onFailure(exception -> handleFailure(exception, startTime, MetricName.initialize));
    }

    private void refresh() {
        final long startTime = clock.millis();

        getFileContentsForDirectory(storedRequestsDirectory)
                .compose(storedIdToRequest -> getFileContentsForDirectory(storedImpressionsDirectory)
                        .map(storedIdToImp ->
                                StoredDataResult.of(storedIdToRequest, storedIdToImp, Collections.emptyList())))
                .onSuccess(storedDataResult -> handleResult(invalidate(storedDataResult), startTime, MetricName.update))
                .onFailure(exception -> handleFailure(exception, startTime, MetricName.update));
    }

    private Void handleResult(StoredDataResult storedDataResult,
                              long startTime,
                              MetricName refreshType) {

        lastResult.set(storedDataResult);

        cacheNotificationListener.save(storedDataResult.getStoredIdToRequest(), storedDataResult.getStoredIdToImp());

        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);

        return null;
    }

    private Future<Void> handleFailure(Throwable exception, long startTime, MetricName refreshType) {
        logger.warn("Error occurred while request to s3 refresh service", exception);

        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);
        metrics.updateSettingsCacheRefreshErrorMetric(cacheType, refreshType);

        return Future.failedFuture(exception);
    }

    private StoredDataResult invalidate(StoredDataResult storedDataResult) {
        final Set<String> invalidatedRequests = getInvalidatedKeys(
                storedDataResult.getStoredIdToRequest(),
                lastResult != null ? lastResult.get().getStoredIdToRequest() : Collections.emptyMap());
        final Set<String> invalidatedImps = getInvalidatedKeys(
                storedDataResult.getStoredIdToImp(),
                lastResult != null ? lastResult.get().getStoredIdToImp() : Collections.emptyMap());

        if (!invalidatedRequests.isEmpty() || !invalidatedImps.isEmpty()) {
            cacheNotificationListener.invalidate(
                    invalidatedRequests.stream().toList(),
                    invalidatedImps.stream().toList()
            );
        }

        return storedDataResult;
    }

    private Future<Set<String>> listFiles(String prefix) {
        final ListObjectsRequest listObjectsRequest =
                ListObjectsRequest.builder().bucket(bucket).prefix(prefix).build();

        return Future.fromCompletionStage(asyncClient.listObjects(listObjectsRequest))
                .map(response -> response.contents().stream().map(S3Object::key).collect(Collectors.toSet()));
    }

    private Future<Map<String, String>> getFileContentsForDirectory(String directory) {
        return listFiles(directory)
                .compose(files ->
                        getFileContents(files)
                                .map(map -> map.entrySet().stream().collect(
                                        Collectors.toMap(
                                                e -> stripFileName(directory, e.getKey()),
                                                Map.Entry::getValue))));
    }

    private String stripFileName(String directory, String name) {
        return name.replace(directory + "/", "").replace(JSON_SUFFIX, "");
    }

    private Future<Map<String, String>> getFileContents(Set<String> fileNames) {
        final List<Future<Tuple2<String, String>>> futureListContents = fileNames.stream()
                .map(fileName -> downloadFile(fileName).map(fileContent -> Tuple2.of(fileName, fileContent)))
                .collect(Collectors.toCollection(ArrayList::new));

        final Future<List<Tuple2<String, String>>> composedFutures =
                CompositeFuture.all(new ArrayList<>(futureListContents)).map(CompositeFuture::list);

        return composedFutures.map(one -> one.stream().collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight)));
    }

    private Future<String> downloadFile(String key) {
        final GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

        return Future.fromCompletionStage(asyncClient.getObject(request, AsyncResponseTransformer.toBytes()))
                .map(BytesWrapper::asUtf8String);
    }
}
