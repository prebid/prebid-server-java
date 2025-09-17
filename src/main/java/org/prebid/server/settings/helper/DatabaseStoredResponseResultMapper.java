package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
<<<<<<< HEAD
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.settings.model.StoredResponseDataResult;
=======
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.util.ObjectUtil;
>>>>>>> 04d9d4a13 (Initial commit)

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
<<<<<<< HEAD
import java.util.Objects;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import java.util.Set;

public class DatabaseStoredResponseResultMapper {

    private DatabaseStoredResponseResultMapper() {
    }

    public static StoredResponseDataResult map(RowSet<Row> rowSet, Set<String> responseIds) {
<<<<<<< HEAD
        final RowIterator<Row> rowIterator = rowSet != null ? rowSet.iterator() : null;
        final List<String> errors = new ArrayList<>();

        if (rowIterator == null || !rowIterator.hasNext()) {
            handleEmptyResult(responseIds, errors);
            return StoredResponseDataResult.of(Collections.emptyMap(), Collections.unmodifiableList(errors));
        }

        final Map<String, String> storedIdToResponse = new HashMap<>(responseIds.size());

        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (row.size() < 2) {
                errors.add("Result set column number is less than expected");
                return StoredResponseDataResult.of(Collections.emptyMap(), Collections.unmodifiableList(errors));
            }

            storedIdToResponse.put(
                    Objects.toString(row.getValue(0), null),
                    Objects.toString(row.getValue(1), null));
        }

        SetUtils.difference(responseIds, storedIdToResponse.keySet())
                .forEach(id -> errors.add("No stored response found for id: " + id));

        return StoredResponseDataResult.of(
                Collections.unmodifiableMap(storedIdToResponse),
                Collections.unmodifiableList(errors));
    }

    private static void handleEmptyResult(Set<String> responseIds, List<String> errors) {
=======
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
>>>>>>> 04d9d4a13 (Initial commit)
        if (responseIds.isEmpty()) {
            errors.add("No stored responses found");
        } else {
            errors.add("No stored responses were found for ids: " + String.join(",", responseIds));
        }
    }
}
