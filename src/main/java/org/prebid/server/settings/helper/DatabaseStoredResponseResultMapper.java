package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseStoredResponseResultMapper {

    private DatabaseStoredResponseResultMapper() {
    }

    public static StoredResponseDataResult map(RowSet<Row> rowSet, Set<String> responseIds) {
        final Map<String, String> storedIdToResponse = new HashMap<>(responseIds.size());
        final List<String> errors = new ArrayList<>();

        final RowIterator<Row> rowIterator = rowSet != null ? rowSet.iterator() : null;
        if (rowIterator == null || !rowIterator.hasNext()) {
            handleEmptyResultError(responseIds, errors);
            return StoredResponseDataResult.of(storedIdToResponse, errors);
        }

        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (row.toJson().size() < 2) {
                errors.add("Result set column number is less than expected");
                return StoredResponseDataResult.of(Collections.emptyMap(), errors);
            }
            final String key = ObjectUtil.getIfNotNull(row.getValue(0), Object::toString);
            final String value = ObjectUtil.getIfNotNull(row.getValue(1), Object::toString);
            storedIdToResponse.put(key, value);
        }

        errors.addAll(responseIds.stream().filter(id -> !storedIdToResponse.containsKey(id))
                .map(id -> "No stored response found for id: " + id)
                .toList());

        return StoredResponseDataResult.of(storedIdToResponse, errors);
    }

    private static void handleEmptyResultError(Set<String> responseIds, List<String> errors) {
        if (responseIds.isEmpty()) {
            errors.add("No stored responses found");
        } else {
            errors.add("No stored responses were found for ids: " + String.join(",", responseIds));
        }
    }
}
