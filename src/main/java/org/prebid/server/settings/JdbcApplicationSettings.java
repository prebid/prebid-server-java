package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.vertx.JdbcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from the database source.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link JdbcApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 */
public class JdbcApplicationSettings implements ApplicationSettings {

    private static final Logger logger = LoggerFactory.getLogger(JdbcApplicationSettings.class);

    private static final String REQUEST_ID_PLACEHOLDER = "%REQUEST_ID_LIST%";
    private static final String IMP_ID_PLACEHOLDER = "%IMP_ID_LIST%";

    private final JdbcClient jdbcClient;

    /**
     * Query to select stored requests and imps by ids, for example:
     * <pre>
     * SELECT reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * UNION ALL
     * SELECT impid, impData, 'imp' as dataType
     *   FROM stored_imps
     *   WHERE impid in (%IMP_ID_LIST%)
     * </pre>
     */
    private final String selectQuery;

    /**
     * Query to select amp stored requests by ids, for example:
     * <pre>
     * SELECT reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * </pre>
     */
    private final String selectAmpQuery;

    public JdbcApplicationSettings(JdbcClient jdbcClient, String selectQuery, String selectAmpQuery) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.selectQuery = Objects.requireNonNull(selectQuery);
        this.selectAmpQuery = Objects.requireNonNull(selectAmpQuery);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future&lt;{@link Account}&gt;}
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT uuid, price_granularity FROM accounts_account where uuid = ? LIMIT 1",
                Collections.singletonList(accountId),
                result -> mapToModelOrError(result, row -> Account.of(row.getString(0), row.getString(1))),
                timeout);
    }

    /**
     * Runs a process to get AdUnit config by id from database
     * and returns {@link Future&lt;{@link String}&gt;}
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1",
                Collections.singletonList(adUnitConfigId),
                result -> mapToModelOrError(result, row -> row.getString(0)),
                timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectQuery, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectAmpQuery, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Transforms the first row of {@link ResultSet} to required object.
     */
    private <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
        if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
            throw new PreBidException("Not found");
        }
        return mapper.apply(result.getResults().get(0));
    }

    /**
     * Fetches stored requests from database for the given query.
     */
    private Future<StoredDataResult> fetchStoredData(String query, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        final Future<StoredDataResult> future;

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            future = Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        } else {
            final List<String> idsQueryParameters = new ArrayList<>();
            IntStream.rangeClosed(1, StringUtils.countMatches(query, REQUEST_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(requestIds));
            IntStream.rangeClosed(1, StringUtils.countMatches(query, IMP_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(impIds));

            final String parametrizedQuery = createParametrizedQuery(query, requestIds.size(), impIds.size());
            future = jdbcClient.executeQuery(parametrizedQuery, idsQueryParameters,
                    result -> mapToStoredRequestResult(result, requestIds, impIds),
                    timeout);
        }

        return future;
    }

    /**
     * Creates parametrized query from query and variable templates, by replacing templateVariable
     * with appropriate number of "?" placeholders.
     */
    private static String createParametrizedQuery(String query, int requestIdsSize, int impIdsSize) {
        return query
                .replace(REQUEST_ID_PLACEHOLDER, parameterHolders(requestIdsSize))
                .replace(IMP_ID_PLACEHOLDER, parameterHolders(impIdsSize));
    }

    /**
     * Returns string for parametrized placeholder
     */
    private static String parameterHolders(int paramsSize) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(0, paramsSize).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    /**
     * Maps {@link ResultSet} to {@link StoredDataResult}. In case of {@link ResultSet} size is less than ids number
     * creates an error for each missing id and add it to result.
     */
    private static StoredDataResult mapToStoredRequestResult(ResultSet rs, Set<String> requestIds,
                                                             Set<String> impIds) {
        final Map<String, String> storedIdToRequest = new HashMap<>(requestIds.size());
        final Map<String, String> storedIdToImp = new HashMap<>(impIds.size());
        final List<String> errors = new ArrayList<>();

        if (rs == null || rs.getResults() == null || rs.getResults().isEmpty()) {
            final String errorRequests = requestIds.isEmpty() ? ""
                    : String.format("stored requests for ids %s", requestIds);
            final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
            final String errorImps = impIds.isEmpty() ? "" : String.format("stored imps for ids %s", impIds);

            errors.add(String.format("No %s%s%s was found", errorRequests, separator, errorImps));
        } else {
            try {
                for (JsonArray result : rs.getResults()) {
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

            errors.addAll(errorsForMissedIds(requestIds, storedIdToRequest, StoredDataType.request));
            errors.addAll(errorsForMissedIds(impIds, storedIdToImp, StoredDataType.imp));
        }

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    /**
     * Returns errors for missed IDs.
     */
    private static List<String> errorsForMissedIds(Set<String> ids, Map<String, String> storedIdToJson,
                                                   StoredDataType type) {
        final List<String> missedIds = ids.stream()
                .filter(id -> !storedIdToJson.containsKey(id))
                .collect(Collectors.toList());

        return missedIds.isEmpty() ? Collections.emptyList() : missedIds.stream()
                .map(id -> String.format("No stored %s found for id: %s", type, id))
                .collect(Collectors.toList());
    }
}
