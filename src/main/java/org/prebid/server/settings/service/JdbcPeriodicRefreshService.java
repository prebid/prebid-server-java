package org.prebid.server.settings.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
public class JdbcPeriodicRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(JdbcPeriodicRefreshService.class);

    private final CacheNotificationListener cacheNotificationListener;
    private final Vertx vertx;
    private final JdbcClient jdbcClient;
    private final long refreshPeriod;

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
     * Example of initialize query:
     * <pre>
     * SELECT id, requestData, type
     * FROM stored_requests
     * WHERE last_updated > ?;
     * <pre>
     * The code will be run periodically to fetch updates from the database.
     * Wildcard "?" would be used to pass last update date automatically.
     */
    private final String updateQuery;
    private final TimeoutFactory timeoutFactory;
    private final long timeout;
    private Instant lastUpdate;

    public JdbcPeriodicRefreshService(CacheNotificationListener cacheNotificationListener,
                                      Vertx vertx, JdbcClient jdbcClient, long refreshPeriod, String initQuery,
                                      String updateQuery, TimeoutFactory timeoutFactory, long timeout) {
        this.cacheNotificationListener = Objects.requireNonNull(cacheNotificationListener);
        this.vertx = Objects.requireNonNull(vertx);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.refreshPeriod = refreshPeriod;
        this.initQuery = Objects.requireNonNull(StringUtils.stripToNull(initQuery));
        this.updateQuery = Objects.requireNonNull(StringUtils.stripToNull(updateQuery));
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.timeout = timeout;
    }

    public void initialize() {
        getAll();
        if (refreshPeriod > 0) {
            vertx.setPeriodic(refreshPeriod, aLong -> refresh());
        }
    }

    private void getAll() {
        lastUpdate = Instant.now();

        jdbcClient.executeQuery(initQuery, Collections.emptyList(),
                JdbcPeriodicRefreshService::mapToStoredRequestResult, createTimeout())
                .map(this::save)
                .recover(JdbcPeriodicRefreshService::failResponse);
    }

    private Void save(StoredDataResult storedDataResult) {
        cacheNotificationListener.save(storedDataResult.getStoredIdToRequest(), storedDataResult.getStoredIdToImp());
        return null;
    }

    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to jdbc refresh service", exception);
        return Future.failedFuture(exception);
    }

    private static StoredDataResult mapToStoredRequestResult(ResultSet resultSet) {
        final Map<String, String> storedIdToRequest = new HashMap<>();
        final Map<String, String> storedIdToImp = new HashMap<>();
        final List<String> errors = new ArrayList<>();

        if (resultSet == null || CollectionUtils.isEmpty(resultSet.getResults())) {
            errors.add("No stored requests found");
        } else {
            try {
                for (JsonArray result : resultSet.getResults()) {
                    final String id = result.getString(0);
                    final String json = result.getString(1);
                    final String typeAsString = result.getString(2);

                    final StoredDataType type;
                    try {
                        type = StoredDataType.valueOf(typeAsString);
                    } catch (IllegalArgumentException e) {
                        logger.error("Result set with id={0} has invalid type: {1}. This will be ignored.", e, id,
                                typeAsString);
                        continue;
                    }

                    if (type == StoredDataType.request) {
                        storedIdToRequest.put(id, json);
                    } else {
                        storedIdToImp.put(id, json);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                errors.add("Result set column number is less than expected");
                return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), errors);
            }
        }
        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    private void refresh() {
        final Instant updateTime = Instant.now();

        // ... WHERE last_updated > ?
        jdbcClient.executeQuery(updateQuery, Collections.singletonList(Date.from(lastUpdate)),
                JdbcPeriodicRefreshService::mapToStoredRequestResult, createTimeout())
                .map(this::invalidate)
                .map(this::save)
                .recover(JdbcPeriodicRefreshService::failResponse);

        lastUpdate = updateTime;
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
