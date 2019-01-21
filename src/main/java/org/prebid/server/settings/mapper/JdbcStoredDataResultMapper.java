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

    public static StoredDataResult map(ResultSet resultSet) {
        final Map<String, String> storedIdToRequest = new HashMap<>();
        final Map<String, String> storedIdToImp = new HashMap<>();
        final List<String> errors = new ArrayList<>();

        if (resultSet == null || CollectionUtils.isEmpty(resultSet.getResults())) {
            errors.add("No stored requests or imps found");
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

    /**
     * Creates an error for each missing id and add it to result.
     */
    public static StoredDataResult mapWithIds(ResultSet resultSet, Set<String> requestIds, Set<String> impIds) {
        final StoredDataResult storedDataResult = map(resultSet);
        final List<String> errors = storedDataResult.getErrors();
        if (errors.isEmpty()) {
            errors.addAll(errorsForMissedIds(requestIds, storedDataResult.getStoredIdToRequest(),
                    StoredDataType.request));
            errors.addAll(errorsForMissedIds(impIds, storedDataResult.getStoredIdToImp(), StoredDataType.imp));
        }

        return storedDataResult;
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
