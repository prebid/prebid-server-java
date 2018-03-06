package org.prebid.server.settings;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AdUnitConfig;
import org.prebid.server.settings.model.SettingsFile;
import org.prebid.server.settings.model.StoredRequestResult;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final Map<String, String> storedRequests;

    private FileApplicationSettings(SettingsFile settingsFile, Map<String, String> storedRequests) {
        this.accounts = toMap(settingsFile.getAccounts(),
                Function.identity(),
                account -> Account.of(account, null));
        this.configs = toMap(settingsFile.getConfigs(),
                AdUnitConfig::getId,
                config -> ObjectUtils.firstNonNull(config.getConfig(), StringUtils.EMPTY));
        this.storedRequests = Objects.requireNonNull(storedRequests);
    }

    /**
     * Instantiate {@link FileApplicationSettings} by and by looking for .json file
     * extension and creates {@link Map} file names without .json extension to file content.
     */
    public static FileApplicationSettings create(FileSystem fileSystem, String settingsFileName,
                                                 String storedRequestsDir) {
        Objects.requireNonNull(fileSystem);
        Objects.requireNonNull(settingsFileName);
        Objects.requireNonNull(storedRequestsDir);

        return new FileApplicationSettings(
                readSettingsFile(fileSystem, settingsFileName),
                readStoredRequests(fileSystem, storedRequestsDir));
    }

    @Override
    public Future<Account> getAccountById(String accountId, GlobalTimeout timeout) {
        return mapValueToFuture(accounts, accountId);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout) {
        return mapValueToFuture(configs, adUnitConfigId);
    }

    /**
     * Creates {@link StoredRequestResult} by checking if any ids are missed in storedRequest map and adding an error
     * to list for each missed Id. Returns {@link Future<StoredRequestResult>} with all loaded files and errors list.
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout) {
        final List<String> errors;
        final List<String> missedIds = ids.stream()
                .filter(s -> !storedRequests.containsKey(s))
                .collect(Collectors.toList());
        if (missedIds.size() > 0) {
            errors = missedIds.stream()
                    .map(id -> String.format("No config found for id: %s", id))
                    .collect(Collectors.toList());
        } else {
            errors = Collections.emptyList();
        }
        return Future.succeededFuture(StoredRequestResult.of(storedRequests, errors));
    }

    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, GlobalTimeout timeout) {
        return getStoredRequestsById(ids, timeout);
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
    private static Map<String, String> readStoredRequests(FileSystem fileSystem, String requestsDir) {
        final List<String> filesPaths = fileSystem.readDirBlocking(requestsDir);

        return filesPaths.stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
    }

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String key) {
        final T value = map.get(key);
        if (value != null) {
            return Future.succeededFuture(value);
        } else {
            return Future.failedFuture(new PreBidException("Not found"));
        }
    }
}
