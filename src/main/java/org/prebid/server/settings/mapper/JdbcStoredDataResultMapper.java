package org.prebid.server.settings.mapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
                        : String.format("stored requests for ids %s", requestIds);
                final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
                final String errorImps = impIds.isEmpty() ? "" : String.format("stored imps for ids %s", impIds);

                errors.add(String.format("No %s%s%s were found", errorRequests, separator, errorImps));
            }
        } else {
            final List<StoredItem> requestStoredItems = new ArrayList<>();
            final List<StoredItem> impStoredItems = new ArrayList<>();

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
                    logger.error("Stored request data with id={0} has invalid type: ''{1}'' and will be ignored.", e,
                            id,
                            typeAsString);
                    continue;
                }

                final StoredItem storedItem = StoredItem.of(fetchedAccountId, id, data);
                if (type == StoredDataType.request) {
                    requestStoredItems.add(storedItem);
                } else {
                    impStoredItems.add(storedItem);
                }
            }

            storedIdToRequest = storedItemsOrAddError(StoredDataType.request, accountId, requestIds, requestStoredItems,
                    errors);
            storedIdToImp = storedItemsOrAddError(StoredDataType.imp, accountId, impIds, impStoredItems, errors);
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
        return map(resultSet, null, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Tries to find stored item which belongs to appropriate account.
     * <p>
     * Additional processing involved because incoming prebid request may not have account defined,
     * so there are two cases:
     * <p>
     * - account is present in request - find stored items of this account or skip it otherwise.
     * <p>
     * - account is not present in request - if multiple items were found - add error, otherwise use found item.
     *
     * @return map of stored ID -> value or populate error.
     */
    private static Map<String, String> storedItemsOrAddError(StoredDataType type, String accountId,
                                                             Set<String> searchIds, List<StoredItem> foundStoredItems,
                                                             List<String> errors) {
        final Map<String, String> result = new HashMap<>(foundStoredItems.size());

        if (searchIds.isEmpty()) {
            foundStoredItems.forEach(storedItem -> result.put(storedItem.getId(), storedItem.getData()));
        } else {
            final Map<String, List<StoredItem>> idToStoredItems = foundStoredItems.stream()
                    .collect(Collectors.groupingBy(StoredItem::getId));

            for (String searchId : searchIds) {
                final List<StoredItem> storedItems = idToStoredItems.get(searchId);
                if (storedItems == null) {
                    errors.add(String.format("No stored %s found for id: %s", type, searchId));
                } else {
                    if (StringUtils.isNotEmpty(accountId)) {
                        final StoredItem storedItem = storedItems.stream()
                                .filter(item -> Objects.equals(item.getAccountId(), accountId))
                                .findAny()
                                .orElse(null);

                        if (storedItem == null) {
                            errors.add(String.format(
                                    "No stored %s found for id: %s for account: %s", type, searchId, accountId));
                        } else {
                            result.put(storedItem.getId(), storedItem.getData());
                        }
                    } else {
                        if (storedItems.size() > 1) {
                            errors.add(String.format(
                                    "Multiple stored %ss found for id: %s but no account ID specified in request",
                                    type, searchId));
                        } else {
                            final StoredItem storedItem = storedItems.get(0);
                            result.put(storedItem.getId(), storedItem.getData());
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * The model helps to reduce multiple rows found for single stored request/imp ID.
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class StoredItem {

        String accountId;

        String id;

        String data;
    }
}
