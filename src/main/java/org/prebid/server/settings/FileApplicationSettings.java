package org.prebid.server.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Category;
import org.prebid.server.settings.model.SettingsFile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredProfileResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from YAML file on file system, stores and serves them in and from the memory.
 * <p>
 * Immediately loads stored request data from local files. These are stored in memory for low-latency reads.
 * This expects each file in the directory to be named "{config_id}.json".
 */
public class FileApplicationSettings implements ApplicationSettings {

    private static final TypeReference<Map<String, Category>> CATEGORY_FORMAT_REFERENCE =
            new TypeReference<>() {
            };
    private static final String JSON_SUFFIX = ".json";

    private final Map<String, Account> accounts;
    private final Map<String, String> storedIdToRequest;
    private final Map<String, String> storedIdToImp;
    private final Map<String, String> storedIdToSeatBid;
    private final Map<String, Map<String, Category>> fileToCategories;

    public FileApplicationSettings(FileSystem fileSystem,
                                   String settingsFileName,
                                   String storedRequestsDir,
                                   String storedImpsDir,
                                   String storedResponsesDir,
                                   String categoriesDir,
                                   JacksonMapper jacksonMapper) {

        final SettingsFile settingsFile = readSettingsFile(
                Objects.requireNonNull(fileSystem),
                Objects.requireNonNull(settingsFileName));

        accounts = toMap(
                settingsFile.getAccounts(),
                Account::getId,
                Function.identity());

        storedIdToRequest = readStoredData(fileSystem, Objects.requireNonNull(storedRequestsDir));
        storedIdToImp = readStoredData(fileSystem, Objects.requireNonNull(storedImpsDir));
        storedIdToSeatBid = readStoredData(fileSystem, Objects.requireNonNull(storedResponsesDir));
        fileToCategories = readCategories(fileSystem, Objects.requireNonNull(categoriesDir), jacksonMapper);
    }

    private static SettingsFile readSettingsFile(FileSystem fileSystem, String fileName) {
        final Buffer buf = fileSystem.readFileBlocking(fileName);
        try {
            return new YAMLMapper().readValue(buf.getBytes(), SettingsFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read file settings", e);
        }
    }

    private static <T, K, U> Map<K, U> toMap(List<T> list, Function<T, K> keyMapper, Function<T, U> valueMapper) {
        return list != null
                ? list.stream().collect(Collectors.toMap(keyMapper, valueMapper))
                : Collections.emptyMap();
    }

    private static Map<String, String> readStoredData(FileSystem fileSystem, String dir) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(
                        filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filepath -> fileSystem.readFileBlocking(filepath).toString()));
    }

    private static Map<String, Map<String, Category>> readCategories(FileSystem fileSystem,
                                                                     String dir,
                                                                     JacksonMapper jacksonMapper) {

        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(
                        filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filepath -> parseCategories(filepath, fileSystem.readFileBlocking(filepath), jacksonMapper)));
    }

    private static Map<String, Category> parseCategories(String filepath,
                                                         Buffer categoriesBuffer,
                                                         JacksonMapper jacksonMapper) {

        try {
            return jacksonMapper.decodeValue(categoriesBuffer, CATEGORY_FORMAT_REFERENCE);
        } catch (DecodeException e) {
            throw new PreBidException("Failed to decode categories for file " + filepath);
        }
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        final Account account = accounts.get(accountId);
        return account != null
                ? Future.succeededFuture(account)
                : Future.failedFuture(new PreBidException("Account not found: " + accountId));
    }

    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)

                ? Future.succeededFuture(StoredDataResult.of(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList()))

                : Future.succeededFuture(StoredDataResult.of(
                existingStoredIdToJson(requestIds, storedIdToRequest),
                existingStoredIdToJson(impIds, storedIdToImp),
                Stream.of(
                                errorsForMissedIds(requestIds, storedIdToRequest, StoredDataType.request),
                                errorsForMissedIds(impIds, storedIdToImp, StoredDataType.imp))
                        .flatMap(Function.identity())
                        .toList()));
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredProfileResult> getProfiles(String accountId,
                                                   Set<String> requestIds,
                                                   Set<String> impIds,
                                                   Timeout timeout) {

        // TODO: implement
        return Future.failedFuture("Not implemented");
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return CollectionUtils.isEmpty(responseIds)

                ? Future.succeededFuture(StoredResponseDataResult.of(Collections.emptyMap(), Collections.emptyList()))

                : Future.succeededFuture(StoredResponseDataResult.of(
                existingStoredIdToJson(responseIds, storedIdToSeatBid),
                errorsForMissedIds(responseIds, storedIdToSeatBid, StoredDataType.seatbid).toList()));
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        final String filename = StringUtils.isNotBlank(publisher)
                ? "%s_%s".formatted(primaryAdServer, publisher)
                : primaryAdServer;

        final Map<String, Category> categoryToId = fileToCategories.get(filename);
        return categoryToId != null
                ? Future.succeededFuture(extractCategoriesIds(categoryToId))
                : Future.failedFuture(new PreBidException(
                "Categories for filename %s were not found".formatted(filename)));
    }

    private static Map<String, String> extractCategoriesIds(Map<String, Category> categoryToId) {
        return categoryToId.entrySet().stream()
                .filter(catToCategory -> catToCategory.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        catToCategory -> catToCategory.getValue().getId()));
    }

    private static Map<String, String> existingStoredIdToJson(Set<String> requestedIds,
                                                              Map<String, String> storedIdToJson) {

        return requestedIds.stream()
                .filter(storedIdToJson::containsKey)
                .collect(Collectors.toMap(Function.identity(), storedIdToJson::get));
    }

    private static Stream<String> errorsForMissedIds(Set<String> ids,
                                                     Map<String, String> storedIdToJson,
                                                     StoredDataType type) {

        return SetUtils.difference(ids, storedIdToJson.keySet()).stream()
                .map(id -> "No stored %s found for id: %s".formatted(type, id));
    }
}
