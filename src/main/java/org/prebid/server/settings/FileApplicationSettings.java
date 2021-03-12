package org.prebid.server.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Category;
import org.prebid.server.settings.model.SettingsFile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
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
            new TypeReference<Map<String, Category>>() {
            };
    private static final String JSON_SUFFIX = ".json";

    private final Map<String, Account> accounts;
    private final Map<String, String> storedIdToRequest;
    private final Map<String, String> storedIdToImp;
    private final Map<String, String> storedIdToSeatBid;
    private final Map<String, Map<String, Category>> fileToCategories;

    public FileApplicationSettings(FileSystem fileSystem, String settingsFileName, String storedRequestsDir,
                                   String storedImpsDir, String storedResponsesDir, String categoriesDir,
                                   JacksonMapper jacksonMapper) {

        final SettingsFile settingsFile = readSettingsFile(Objects.requireNonNull(fileSystem),
                Objects.requireNonNull(settingsFileName));

        accounts = toMap(settingsFile.getAccounts(),
                Account::getId,
                Function.identity());

        this.storedIdToRequest = readStoredData(fileSystem, Objects.requireNonNull(storedRequestsDir));
        this.storedIdToImp = readStoredData(fileSystem, Objects.requireNonNull(storedImpsDir));
        this.storedIdToSeatBid = readStoredData(fileSystem, Objects.requireNonNull(storedResponsesDir));
        this.fileToCategories = readCategories(fileSystem, Objects.requireNonNull(categoriesDir), jacksonMapper);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return mapValueToFuture(accounts, accountId, "Account");
    }

    /**
     * Creates {@link StoredDataResult} by checking if any ids are missed in storedRequest map
     * and adding an error to list for each missed Id
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;} with all loaded files and errors list.
     */
    @Override
    public Future<StoredDataResult> getStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                  Timeout timeout) {
        return Future.succeededFuture(CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)
                ? StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList())
                : StoredDataResult.of(
                existingStoredIdToJson(requestIds, storedIdToRequest),
                existingStoredIdToJson(impIds, storedIdToImp),
                Stream.of(
                        errorsForMissedIds(requestIds, storedIdToRequest, StoredDataType.request),
                        errorsForMissedIds(impIds, storedIdToImp, StoredDataType.imp))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())));
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        return getStoredData(accountId, requestIds, Collections.emptySet(), timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                       Timeout timeout) {
        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        final String filename = StringUtils.isNotBlank(publisher)
                ? String.format("%s_%s", primaryAdServer, publisher)
                : primaryAdServer;
        final Map<String, Category> categoryToId = fileToCategories.get(filename);
        return categoryToId != null
                ? Future.succeededFuture(extractCategoriesIds(categoryToId))
                : Future.failedFuture(new PreBidException(
                String.format("Categories for filename %s were not found", filename)));
    }

    private static Map<String, String> extractCategoriesIds(Map<String, Category> categoryToId) {
        return categoryToId.entrySet().stream()
                .filter(catToCategory -> catToCategory.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        catToCategory -> catToCategory.getValue().getId()));
    }

    /**
     * Creates {@link StoredResponseDataResult} by checking if any ids are missed in storedResponse map
     * and adding an error to list for each missed Id
     * and returns {@link Future&lt;{@link StoredResponseDataResult }&gt;} with all loaded files and errors list.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return Future.succeededFuture(CollectionUtils.isEmpty(responseIds)
                ? StoredResponseDataResult.of(Collections.emptyMap(), Collections.emptyList())
                : StoredResponseDataResult.of(
                existingStoredIdToJson(responseIds, storedIdToSeatBid),
                errorsForMissedIds(responseIds, storedIdToSeatBid, StoredDataType.seatbid)));
    }

    private static <T, K, U> Map<K, U> toMap(List<T> list, Function<T, K> keyMapper, Function<T, U> valueMapper) {
        return list != null ? list.stream().collect(Collectors.toMap(keyMapper, valueMapper)) : Collections.emptyMap();
    }

    /**
     * Reading YAML settings file.
     */
    private static SettingsFile readSettingsFile(FileSystem fileSystem, String fileName) {
        final Buffer buf = fileSystem.readFileBlocking(fileName);
        try {
            return new YAMLMapper().readValue(buf.getBytes(), SettingsFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read file settings", e);
        }
    }

    /**
     * Reads files with .json extension in configured directory and creates {@link Map} where key is a file name
     * without .json extension and value is file content.
     */
    private static Map<String, String> readStoredData(FileSystem fileSystem, String dir) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    /**
     * Reads files with .json extension in configured directory and creates {@link Map} where key is a file name
     * without .json and value is file content parsed to a {@link Map} where key is category and value is
     * {@link Category}.
     */
    private static Map<String, Map<String, Category>> readCategories(FileSystem fileSystem, String dir,
                                                                     JacksonMapper jacksonMapper) {
        return fileSystem.readDirBlocking(dir).stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> parseCategories(filename, fileSystem.readFileBlocking(filename), jacksonMapper)));
    }

    /**
     * Parses {@link Buffer} to a {@link Map} where key is category and value {@link Category}.
     */
    private static Map<String, Category> parseCategories(String fileName, Buffer categoriesBuffer,
                                                         JacksonMapper jacksonMapper) {
        try {
            return jacksonMapper.decodeValue(categoriesBuffer, CATEGORY_FORMAT_REFERENCE);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Failed to decode categories for file %s", fileName));
        }
    }

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String id, String errorPrefix) {
        final T value = map.get(id);
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException(String.format("%s not found: %s", errorPrefix, id)));
    }

    /**
     * Returns corresponding stored id with json.
     */
    private static Map<String, String> existingStoredIdToJson(Set<String> requestedIds,
                                                              Map<String, String> storedIdToJson) {
        return requestedIds.stream()
                .filter(storedIdToJson::containsKey)
                .collect(Collectors.toMap(Function.identity(), storedIdToJson::get));
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
