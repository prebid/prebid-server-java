package org.prebid.server.settings.helper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for mapping {@link ResultSet} to {@link StoredDataResult}.
 */
public class JdbcStoredDataResultMapper {

    private static final Logger logger = LoggerFactory.getLogger(JdbcStoredDataResultMapper.class);

    private JdbcStoredDataResultMapper() {
    }

    /**
     * Maps {@link ResultSet} to {@link StoredDataResult} and creates an error for each missing ID and add it to result.
     *
     * @param resultSet  - incoming Result Set representing a result of SQL query
     * @param accountId  - an account ID extracted from request
     * @param requestIds - a specified set of stored requests' IDs. Adds error for each ID missing in result set
     * @param impIds     - a specified set of stored imps' IDs. Adds error for each ID missing in result set
     * @return - a {@link StoredDataResult} object
     * <p>
     * Note: mapper should never throws exception in case of using
     * {@link org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient}.
     */
    public static StoredDataResult map(ResultSet resultSet, String accountId, Set<String> requestIds,
                                       Set<String> impIds) {
        final Map<String, String> storedIdToRequest;
        final Map<String, String> storedIdToImp;
        final List<String> errors = new ArrayList<>();

        if (resultSet == null || CollectionUtils.isEmpty(resultSet.getResults())) {
            storedIdToRequest = Collections.emptyMap();
            storedIdToImp = Collections.emptyMap();

            if (requestIds.isEmpty() && impIds.isEmpty()) {
                errors.add("No stored requests or imps were found");
            } else {
                final String errorRequests = requestIds.isEmpty() ? ""
                        : "stored requests for ids " + requestIds;
                final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
                final String errorImps = impIds.isEmpty() ? "" : "stored imps for ids " + impIds;

                errors.add("No %s%s%s were found".formatted(errorRequests, separator, errorImps));
            }
        } else {
            final Map<String, Set<StoredItem>> requestIdToStoredItems = new HashMap<>();
            final Map<String, Set<StoredItem>> impIdToStoredItems = new HashMap<>();

            for (JsonArray result : resultSet.getResults()) {
                final String fetchedAccountId;
                final String id;
                final String data;
                final String typeAsString;
                try {
                    fetchedAccountId = result.getString(0);
                    id = result.getString(1);
                    data = result.getString(2);
                    typeAsString = result.getString(3);
                } catch (IndexOutOfBoundsException | ClassCastException e) {
                    final String message = "Error occurred while mapping stored request data";
                    logger.error(message, e);
                    errors.add(message);
                    return StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), errors);
                }

                final StoredDataType type;
                try {
                    type = StoredDataType.valueOf(typeAsString);
                } catch (IllegalArgumentException e) {
                    logger.error("Stored request data with id={} has invalid type: ''{}'' and will be ignored.", e,
                            id, typeAsString);
                    continue;
                }

                if (type == StoredDataType.request) {
                    addStoredItem(fetchedAccountId, id, data, requestIdToStoredItems);
                } else {
                    addStoredItem(fetchedAccountId, id, data, impIdToStoredItems);
                }
            }

            storedIdToRequest = storedItemsOrAddError(StoredDataType.request, accountId, requestIds,
                    requestIdToStoredItems, errors);
            storedIdToImp = storedItemsOrAddError(StoredDataType.imp, accountId, impIds,
                    impIdToStoredItems, errors);
        }

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    /**
     * Overloaded method for cases when no specific IDs are required, e.g. fetching all records.
     *
     * @param resultSet - incoming {@link ResultSet} representing a result of SQL query.
     * @return - a {@link StoredDataResult} object.
     */
    public static StoredDataResult map(ResultSet resultSet) {
        return map(resultSet, null, Collections.emptySet(), Collections.emptySet());
    }

    private static void addStoredItem(String accountId, String id, String data,
                                      Map<String, Set<StoredItem>> idToStoredItems) {
        final StoredItem storedItem = StoredItem.of(accountId, data);

        final Set<StoredItem> storedItems = idToStoredItems.get(id);
        if (storedItems == null) {
            idToStoredItems.put(id, new HashSet<>(Collections.singleton(storedItem)));
        } else {
            storedItems.add(storedItem);
        }
    }

    /**
     * Returns map of stored ID -> value or populates error.
     */
    private static Map<String, String> storedItemsOrAddError(StoredDataType type,
                                                             String accountId,
                                                             Set<String> searchIds,
                                                             Map<String, Set<StoredItem>> foundIdToStoredItems,
                                                             List<String> errors) {
        final Map<String, String> result = new HashMap<>();

        if (searchIds.isEmpty()) {
            for (Map.Entry<String, Set<StoredItem>> entry : foundIdToStoredItems.entrySet()) {
                entry.getValue().forEach(storedItem -> result.put(entry.getKey(), storedItem.getData()));
            }
        } else {
            for (String id : searchIds) {
                try {
                    final StoredItem resolvedStoredItem = StoredItemResolver.resolve(type, accountId, id,
                            foundIdToStoredItems.get(id));
                    result.put(id, resolvedStoredItem.getData());
                } catch (PreBidException e) {
                    errors.add(e.getMessage());
                }
            }
        }

        return result;
    }
}
