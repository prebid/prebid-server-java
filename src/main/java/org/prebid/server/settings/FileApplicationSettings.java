package org.prebid.server.settings;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AdUnitConfig;
import org.prebid.server.settings.model.SettingsFile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredDataType;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

    private static final String JSON_SUFFIX = ".json";

    private final Map<String, Account> accounts;
    private final Map<String, String> configs;
    private final Map<String, String> storedIdToRequest;
    private final Map<String, String> storedIdToImp;
    private final Map<String, String> storedIdToSeatBid;

    public FileApplicationSettings(FileSystem fileSystem, String settingsFileName, String storedRequestsDir,
                                   String storedImpsDir, String storedResponsesDir) {

        final SettingsFile settingsFile = readSettingsFile(Objects.requireNonNull(fileSystem),
                Objects.requireNonNull(settingsFileName));

        accounts = toMap(settingsFile.getAccounts(),
                Account::getId,
                Function.identity());

        configs = toMap(settingsFile.getConfigs(),
                AdUnitConfig::getId,
                config -> ObjectUtils.firstNonNull(config.getConfig(), StringUtils.EMPTY));

        this.storedIdToRequest = readStoredData(fileSystem, Objects.requireNonNull(storedRequestsDir));
        this.storedIdToImp = readStoredData(fileSystem, Objects.requireNonNull(storedImpsDir));
        this.storedIdToSeatBid = readStoredData(fileSystem, Objects.requireNonNull(storedResponsesDir));
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return mapValueToFuture(accounts, accountId);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return mapValueToFuture(configs, adUnitConfigId);
    }

    /**
     * Creates {@link StoredDataResult} by checking if any ids are missed in storedRequest map
     * and adding an error to list for each missed Id
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;} with all loaded files and errors list.
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
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

    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return getStoredData(requestIds, Collections.emptySet(), timeout);
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

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String key) {
        final T value = map.get(key);
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException("Not found"));
    }

    /**
     * Returns corresponding stored id with json.
     */
    private static Map<String, String> existingStoredIdToJson(Set<String> requestedIds,
                                                              Map<String, String> storedIdToJson) {
        final Map<String, String> idToJson = new HashMap<>();
        for (String id : requestedIds) {
            final String json = storedIdToJson.get(id);
            if (StringUtils.isNotBlank(json)) {
                idToJson.put(id, json);
            }
        }
        return idToJson;
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
