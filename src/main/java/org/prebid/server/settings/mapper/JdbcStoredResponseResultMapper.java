package org.prebid.server.settings.mapper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JdbcStoredResponseResultMapper {

    private JdbcStoredResponseResultMapper() {
    }

    public static StoredResponseDataResult map(ResultSet resultSet, Set<String> responseIds) {
        final Map<String, String> storedIdToResponse = new HashMap<>(responseIds.size());
        final List<String> errors = new ArrayList<>();

        if (resultSet == null || CollectionUtils.isEmpty(resultSet.getResults())) {
            handleEmptyResultError(responseIds, errors);
        } else {
            try {
                for (JsonArray result : resultSet.getResults()) {
                    storedIdToResponse.put(result.getString(0), result.getString(1));
                }
            } catch (IndexOutOfBoundsException e) {
                errors.add("Result set column number is less than expected");
                return StoredResponseDataResult.of(Collections.emptyMap(), errors);
            }
            errors.addAll(responseIds.stream().filter(id -> !storedIdToResponse.containsKey(id))
                    .map(id -> String.format("No stored response found for id: %s", id))
                    .collect(Collectors.toList()));
        }

        return StoredResponseDataResult.of(storedIdToResponse, errors);
    }

    private static void handleEmptyResultError(Set<String> responseIds, List<String> errors) {
        if (responseIds.isEmpty()) {
            errors.add("No stored responses found");
        } else {
            errors.add(String.format("No stored responses were found for ids: %s", String.join(",", responseIds)));
        }
    }
}
