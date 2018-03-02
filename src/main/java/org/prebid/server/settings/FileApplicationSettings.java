package org.prebid.server.settings;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AdUnitConfig;
import org.prebid.server.settings.model.SettingsFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from YAML file on file system, stores and serves them in and from the memory.
 */
public class FileApplicationSettings implements ApplicationSettings {

    private final Map<String, Account> accounts;
    private final Map<String, String> configs;

    private FileApplicationSettings(SettingsFile settingsFile) {
        this.accounts = toMap(settingsFile.getAccounts(),
                Function.identity(),
                account -> Account.of(account, null));
        this.configs = toMap(settingsFile.getConfigs(),
                AdUnitConfig::getId,
                config -> config.getConfig() != null ? config.getConfig() : "");
    }

    private static <T, K, U> Map<K, U> toMap(List<T> list, Function<T, K> keyMapper, Function<T, U> valueMapper) {
        return list != null ? list.stream().collect(Collectors.toMap(keyMapper, valueMapper)) : Collections.emptyMap();
    }

    /**
     * Instantiate {@link FileApplicationSettings} by reading YAML setting file.
     */
    public static FileApplicationSettings create(FileSystem fileSystem, String fileName) {
        Objects.requireNonNull(fileSystem);
        Objects.requireNonNull(fileName);

        final Buffer buf = fileSystem.readFileBlocking(fileName);
        try {
            final SettingsFile settingsFile = new YAMLMapper().readValue(buf.getBytes(), SettingsFile.class);
            return new FileApplicationSettings(settingsFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Couldn't read file settings", e);
        }
    }

    @Override
    public Future<Account> getAccountById(String accountId, GlobalTimeout timeout) {
        return mapValueToFuture(accounts, accountId);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout) {
        return mapValueToFuture(configs, adUnitConfigId);
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
