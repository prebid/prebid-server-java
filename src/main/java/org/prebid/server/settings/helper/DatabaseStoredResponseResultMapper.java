package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DatabaseStoredResponseResultMapper {

    private DatabaseStoredResponseResultMapper() {
    }

    public static StoredResponseDataResult map(RowSet<Row> rowSet, Set<String> responseIds) {
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
        if (responseIds.isEmpty()) {
            errors.add("No stored responses found");
        } else {
            errors.add("No stored responses were found for ids: " + String.join(",", responseIds));
        }
    }
}
