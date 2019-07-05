package org.prebid.server.settings.mapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class that maps {@link ResultSet} to {@link StoredDataResult}.
 */
public class JdbcStoredDataResultMapper {

    private static final Logger logger = LoggerFactory.getLogger(JdbcStoredDataResultMapper.class);

    private JdbcStoredDataResultMapper() {
    }

    /**
     * Maps {@link ResultSet} to {@link StoredDataResult} and creates an error for each missing id and add it to result.
     *
     * @param resultSet  - incoming Result Set representing a result of SQL query
     * @param requestIds - a specified set of stored requests' ids. Adds error for each ID missing in result set
     * @param impIds     - a specified set of stored imps' ids. Adds error for each ID missing in result set
     * @return - a {@link StoredDataResult} object
     * <p>
     * Note: mapper should never throws exception in case of using
     * {@link org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient}.
     */
    public static StoredDataResult map(ResultSet resultSet, Set<String> requestIds, Set<String> impIds) {
        final Map<String, String> storedIdToRequest = new HashMap<>(requestIds.size());
        final Map<String, String> storedIdToImp = new HashMap<>(impIds.size());
        final List<String> errors = new ArrayList<>();

        if (resultSet == null || CollectionUtils.isEmpty(resultSet.getResults())) {
            if (requestIds.isEmpty() && impIds.isEmpty()) {
                errors.add("No stored requests or imps found");
            } else {
                final String errorRequests = requestIds.isEmpty() ? ""
                        : String.format("stored requests for ids %s", requestIds);
                final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
                final String errorImps = impIds.isEmpty() ? "" : String.format("stored imps for ids %s", impIds);

                errors.add(String.format("No %s%s%s were found", errorRequests, separator, errorImps));
            }
        } else {
            for (JsonArray result : resultSet.getResults()) {
                final String id;
                final String json;
                final String typeAsString;
                try {
                    id = result.getString(0);
                    json = result.getString(1);
                    typeAsString = result.getString(2);
                } catch (IndexOutOfBoundsException e) {
                    errors.add("Result set column number is less than expected");
                    return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), errors);
                }

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

            errors.addAll(errorsForMissedIds(requestIds, storedIdToRequest, StoredDataType.request));
            errors.addAll(errorsForMissedIds(impIds, storedIdToImp, StoredDataType.imp));
        }

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    /**
     * Overloaded method for cases when no specific IDs are required, e.g. fetching all records.
     *
     * @param resultSet - incoming Result Set representing a result of SQL query
     * @return - a {@link StoredDataResult} object
     */
    public static StoredDataResult map(ResultSet resultSet) {
        return map(resultSet, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Return errors for missed IDs.
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
