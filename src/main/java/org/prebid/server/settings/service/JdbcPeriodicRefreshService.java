package org.prebid.server.settings.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.helper.JdbcStoredDataResultMapper;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * Service that periodically calls database for stored request updates.
 * If refreshRate is negative, then the data will never be refreshed.
 * <p>
 * The Queries should return a ResultSet with the following columns and types:
 * <pre>
 * 1. id: string
 * 2. data: JSON
 * 3. type: string ("request" or "imp")
 * </pre>
 *
 * <p>
 * If data is empty or the JSON "null", then the ID will be invalidated (e.g. a deletion).
 * If data is not empty, depending on TYPE, it should be put to corresponding map with ID as a key and DATA as value.
 * </p>
 */
public class JdbcPeriodicRefreshService implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(JdbcPeriodicRefreshService.class);

    /**
     * Example of initialize query:
     * <pre>
     * SELECT id, requestData, type
     * FROM stored_requests;
     * <pre>
     * This query will be run once on startup to fetch _all_ known Stored Request data from the database.
     */
    private final String initQuery;
    /**
     * Example of update query:
     * <pre>
     * SELECT id, requestData, type
     * FROM stored_requests
     * WHERE last_updated > ?;
     * <pre>
     * The code will be run periodically to fetch updates from the database.
     * Wildcard "?" would be used to pass last update date automatically.
     */
    private final String updateQuery;
    private final long refreshPeriod;
    private final long timeout;
    private final MetricName cacheType;
    private final CacheNotificationListener cacheNotificationListener;
    private final Vertx vertx;
    private final JdbcClient jdbcClient;
    private final TimeoutFactory timeoutFactory;
    private final Metrics metrics;
    private final Clock clock;

    private Instant lastUpdate;

    public JdbcPeriodicRefreshService(String initQuery,
                                      String updateQuery,
                                      long refreshPeriod,
                                      long timeout,
                                      MetricName cacheType,
                                      CacheNotificationListener cacheNotificationListener,
                                      Vertx vertx,
                                      JdbcClient jdbcClient,
                                      TimeoutFactory timeoutFactory,
                                      Metrics metrics,
                                      Clock clock) {

        this.initQuery = Objects.requireNonNull(StringUtils.stripToNull(initQuery));
        this.updateQuery = Objects.requireNonNull(StringUtils.stripToNull(updateQuery));
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.cacheType = Objects.requireNonNull(cacheType);
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.vertx = Objects.requireNonNull(vertx);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void initialize() {
        getAll();
        if (refreshPeriod > 0) {
            vertx.setPeriodic(refreshPeriod, aLong -> refresh());
        }
    }

    private void getAll() {
        final long startTime = clock.millis();

        jdbcClient.executeQuery(
                initQuery,
                Collections.emptyList(),
                JdbcStoredDataResultMapper::map,
                createTimeout())
                .map(storedDataResult ->
                        handleResult(storedDataResult, Instant.now(clock), startTime, MetricName.initialize))
                .recover(exception -> handleFailure(exception, startTime, MetricName.initialize));
    }

    private Void handleResult(StoredDataResult storedDataResult,
                              Instant updateTime,
                              long startTime,
                              MetricName refreshType) {

        cacheNotificationListener.save(storedDataResult.getStoredIdToRequest(), storedDataResult.getStoredIdToImp());
        lastUpdate = updateTime;

        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);

        return null;
    }

    private Future<Void> handleFailure(Throwable exception, long startTime, MetricName refreshType) {
        logger.warn("Error occurred while request to jdbc refresh service", exception);

        metrics.updateSettingsCacheRefreshTime(cacheType, refreshType, clock.millis() - startTime);
        metrics.updateSettingsCacheRefreshErrorMetric(cacheType, refreshType);

        return Future.failedFuture(exception);
    }

    private void refresh() {
        final Instant updateTime = Instant.now(clock);
        final long startTime = clock.millis();

        jdbcClient.executeQuery(
                updateQuery,
                Collections.singletonList(Date.from(lastUpdate)),
                JdbcStoredDataResultMapper::map,
                createTimeout())
                .map(storedDataResult ->
                        handleResult(invalidate(storedDataResult), updateTime, startTime, MetricName.update))
                .recover(exception -> handleFailure(exception, startTime, MetricName.update));
    }

    private StoredDataResult invalidate(StoredDataResult storedDataResult) {
        final List<String> invalidatedRequests = getInvalidatedKeys(storedDataResult.getStoredIdToRequest());
        final List<String> invalidatedImps = getInvalidatedKeys(storedDataResult.getStoredIdToImp());

        if (!invalidatedRequests.isEmpty() || !invalidatedImps.isEmpty()) {
            cacheNotificationListener.invalidate(invalidatedRequests, invalidatedImps);
        }

        final Map<String, String> requestsToSave = removeFromMap(storedDataResult.getStoredIdToRequest(),
                invalidatedRequests);
        final Map<String, String> impsToSave = removeFromMap(storedDataResult.getStoredIdToImp(), invalidatedImps);

        return StoredDataResult.of(requestsToSave, impsToSave, storedDataResult.getErrors());
    }

    private static List<String> getInvalidatedKeys(Map<String, String> changesMap) {
        return changesMap.entrySet().stream()
                .filter(entry -> StringUtils.isBlank(entry.getValue())
                        || StringUtils.equalsIgnoreCase(entry.getValue(), "null"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static Map<String, String> removeFromMap(Map<String, String> map, List<String> invalidatedKeys) {
        return map.entrySet().stream()
                .filter(entry -> !invalidatedKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Timeout createTimeout() {
        return timeoutFactory.create(timeout);
    }
}
