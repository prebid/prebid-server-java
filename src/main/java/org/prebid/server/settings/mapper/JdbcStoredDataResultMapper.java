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
     * Creates an error for each missing id and add it to result.
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

                errors.add(String.format("No %s%s%s was found", errorRequests, separator, errorImps));
            }
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

            errors.addAll(errorsForMissedIds(requestIds, storedIdToRequest, StoredDataType.request));
            errors.addAll(errorsForMissedIds(impIds, storedIdToImp, StoredDataType.imp));
        }

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    public static StoredDataResult map(ResultSet resultSet) {
        return map(resultSet, Collections.emptySet(), Collections.emptySet());
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
