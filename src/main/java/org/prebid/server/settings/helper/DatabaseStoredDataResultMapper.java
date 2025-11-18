package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredItem;
import org.prebid.server.vertx.database.CircuitBreakerSecuredDatabaseClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DatabaseStoredDataResultMapper {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseStoredDataResultMapper.class);

    private DatabaseStoredDataResultMapper() {
    }

    /**
     * Overloaded method for cases when no specific IDs are required, e.g. fetching all records.
     */
    public static StoredDataResult<String> map(RowSet<Row> resultSet) {
        return map(resultSet, null, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Note: mapper should never throw exception in case of using
     * {@link CircuitBreakerSecuredDatabaseClient}.
     */
    public static StoredDataResult<String> map(RowSet<Row> rowSet,
                                               String accountId,
                                               Set<String> requestIds,
                                               Set<String> impIds) {

        final RowIterator<Row> rowIterator = rowSet != null ? rowSet.iterator() : null;
        final List<String> errors = new ArrayList<>();

        if (rowIterator == null || !rowIterator.hasNext()) {
            handleEmptyResult(requestIds, impIds, errors);

            return StoredDataResult.of(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.unmodifiableList(errors));
        }

        final Map<String, Set<StoredItem<String>>> requestIdToStoredItems = new HashMap<>();
        final Map<String, Set<StoredItem<String>>> impIdToStoredItems = new HashMap<>();

        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (row.size() < 4) {
                final String message = "Error occurred while mapping stored request data: some columns are missing";
                logger.error(message);
                errors.add(message);

                return StoredDataResult.of(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.unmodifiableList(errors));
            }

            final String fetchedAccountId = Objects.toString(row.getValue(0), null);
            final String id = Objects.toString(row.getValue(1), null);
            final String data = Objects.toString(row.getValue(2), null);
            final String typeAsString = Objects.toString(row.getValue(3), null);

            final StoredDataType type;
            try {
                type = StoredDataType.valueOf(typeAsString);
            } catch (IllegalArgumentException e) {
                logger.error("Stored request data with id={} has invalid type: ''{}'' and will be ignored.",
                        e, id, typeAsString);
                continue;
            }

            if (type == StoredDataType.request) {
                addStoredItem(fetchedAccountId, id, data, requestIdToStoredItems);
            } else {
                addStoredItem(fetchedAccountId, id, data, impIdToStoredItems);
            }
        }

        return StoredDataResult.of(
                storedItemsOrAddError(
                        StoredDataType.request,
                        accountId,
                        requestIds,
                        requestIdToStoredItems,
                        errors),
                storedItemsOrAddError(
                        StoredDataType.imp,
                        accountId,
                        impIds,
                        impIdToStoredItems,
                        errors),
                Collections.unmodifiableList(errors));
    }

    private static void handleEmptyResult(Set<String> requestIds, Set<String> impIds, List<String> errors) {
        if (requestIds.isEmpty() && impIds.isEmpty()) {
            errors.add("No stored requests or imps were found");
        } else {
            final String errorRequests = requestIds.isEmpty()
                    ? ""
                    : "stored requests for ids " + requestIds;
            final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
            final String errorImps = impIds.isEmpty() ? "" : "stored imps for ids " + impIds;

            errors.add("No %s%s%s were found".formatted(errorRequests, separator, errorImps));
        }
    }

    private static void addStoredItem(String accountId,
                                      String id,
                                      String data,
                                      Map<String, Set<StoredItem<String>>> idToStoredItems) {

        idToStoredItems.computeIfAbsent(id, key -> new HashSet<>()).add(StoredItem.of(accountId, data));
    }

    private static Map<String, String> storedItemsOrAddError(StoredDataType type,
                                                             String accountId,
                                                             Set<String> searchIds,
                                                             Map<String, Set<StoredItem<String>>> foundIdToStoredItems,
                                                             List<String> errors) {

        final Map<String, String> result = new HashMap<>();

        if (searchIds.isEmpty()) {
            foundIdToStoredItems.forEach((id, storedItems) -> {
                for (StoredItem<String> storedItem : storedItems) {
                    result.put(id, storedItem.getData());
                }
            });

            return Collections.unmodifiableMap(result);
        }

        for (String id : searchIds) {
            try {
                final StoredItem<String> resolvedStoredItem = StoredItemResolver
                        .resolve("stored " + type.toString(), accountId, id, foundIdToStoredItems.get(id));

                result.put(id, resolvedStoredItem.getData());
            } catch (PreBidException e) {
                errors.add(e.getMessage());
            }
        }

        return Collections.unmodifiableMap(result);
    }
}
