package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;
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

public class DatabaseProfilesResultMapper {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseProfilesResultMapper.class);

    private DatabaseProfilesResultMapper() {
    }

    public static StoredDataResult<Profile> map(RowSet<Row> resultSet) {
        return map(resultSet, null, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Note: mapper should never throw exception in case of using
     * {@link CircuitBreakerSecuredDatabaseClient}.
     */
    public static StoredDataResult<Profile> map(RowSet<Row> rowSet,
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

        final Map<String, Set<StoredItem<Profile>>> requestIdToProfiles = new HashMap<>();
        final Map<String, Set<StoredItem<Profile>>> impIdToProfiles = new HashMap<>();

        while (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            if (row.size() < 5) {
                final String message = "Error occurred while mapping profiles: some columns are missing";
                logger.error(message);
                errors.add(message);

                return StoredDataResult.of(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.unmodifiableList(errors));
            }

            final String fetchedAccountId = Objects.toString(row.getValue(0), null);
            final String id = Objects.toString(row.getValue(1), null);
            final String profileBody = Objects.toString(row.getValue(2), null);
            final String mergePrecedenceAsString = Objects.toString(row.getValue(3), null);
            final String typeAsString = Objects.toString(row.getValue(4), null);

            final Profile.Type type;
            final Profile.MergePrecedence mergePrecedence;
            try {
                type = Profile.Type.valueOf(typeAsString);
                mergePrecedence = Profile.MergePrecedence.valueOf(mergePrecedenceAsString);
            } catch (IllegalArgumentException e) {
                logger.error("Profile with id={} has invalid value: ''{}'' and will be ignored.",
                        e, id, typeAsString);
                continue;
            }

            final Profile profile = Profile.of(type, mergePrecedence, profileBody);

            if (type == Profile.Type.REQUEST) {
                addStoredItem(fetchedAccountId, id, profile, requestIdToProfiles);
            } else {
                addStoredItem(fetchedAccountId, id, profile, impIdToProfiles);
            }
        }

        return StoredDataResult.of(
                storedItemsOrAddError(
                        Profile.Type.REQUEST,
                        accountId,
                        requestIds,
                        requestIdToProfiles,
                        errors),
                storedItemsOrAddError(
                        Profile.Type.IMP,
                        accountId,
                        impIds,
                        impIdToProfiles,
                        errors),
                Collections.unmodifiableList(errors));
    }

    private static void handleEmptyResult(Set<String> requestIds, Set<String> impIds, List<String> errors) {
        if (requestIds.isEmpty() && impIds.isEmpty()) {
            errors.add("No profiles were found");
        } else {
            final String errorRequests = requestIds.isEmpty()
                    ? ""
                    : "request profiles for ids " + requestIds;
            final String separator = requestIds.isEmpty() || impIds.isEmpty() ? "" : " and ";
            final String errorImps = impIds.isEmpty() ? "" : "imp profiles for ids " + impIds;

            errors.add("No %s%s%s were found".formatted(errorRequests, separator, errorImps));
        }
    }

    private static void addStoredItem(String accountId,
                                      String id,
                                      Profile profile,
                                      Map<String, Set<StoredItem<Profile>>> idToStoredItems) {

        idToStoredItems.computeIfAbsent(id, key -> new HashSet<>()).add(StoredItem.of(accountId, profile));
    }

    private static Map<String, Profile> storedItemsOrAddError(
            Profile.Type type,
            String accountId,
            Set<String> searchIds,
            Map<String, Set<StoredItem<Profile>>> foundIdToStoredItems,
            List<String> errors) {

        final Map<String, Profile> result = new HashMap<>();

        if (searchIds.isEmpty()) {
            foundIdToStoredItems.forEach((id, storedItems) -> {
                for (StoredItem<Profile> storedItem : storedItems) {
                    result.put(id, storedItem.getData());
                }
            });

            return Collections.unmodifiableMap(result);
        }

        for (String id : searchIds) {
            try {
                final StoredItem<Profile> resolvedStoredItem = StoredItemResolver
                        .resolve(type.toString(), accountId, id, foundIdToStoredItems.get(id));

                result.put(id, resolvedStoredItem.getData());
            } catch (PreBidException e) {
                errors.add(e.getMessage());
            }
        }

        return Collections.unmodifiableMap(result);
    }
}
