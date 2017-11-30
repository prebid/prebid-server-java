package org.rtb.vexing.settings;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.settings.model.Account;
import org.rtb.vexing.settings.model.SettingsFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

class FileApplicationSettings implements ApplicationSettings {

    private final Map<String, Account> accounts;
    private final Map<String, String> configs;

    private FileApplicationSettings(SettingsFile settingsFile) {
        this.accounts = toMap(settingsFile.accounts,
                Function.identity(),
                account -> Account.builder().id(account).build());
        this.configs = toMap(settingsFile.configs,
                config -> config.id,
                config -> config.config != null ? config.config : "");
    }

    private static <T, K, U> Map<K, U> toMap(List<T> list, Function<T, K> keyMapper, Function<T, U> valueMapper) {
        return list != null ? list.stream().collect(Collectors.toMap(keyMapper, valueMapper)) : Collections.emptyMap();
    }

    public static Future<FileApplicationSettings> create(FileSystem fileSystem, String fileName) {
        Objects.requireNonNull(fileSystem);
        Objects.requireNonNull(fileName);

        final Buffer buf = fileSystem.readFileBlocking(fileName);
        try {
            final SettingsFile settingsFile = new YAMLMapper().readValue(buf.getBytes(), SettingsFile.class);
            return Future.succeededFuture(new FileApplicationSettings(settingsFile));
        } catch (IOException e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Account> getAccountById(String accountId) {
        return mapValueToFuture(accounts, accountId);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId) {
        return mapValueToFuture(configs, adUnitConfigId);
    }

    private static <T> Future<T> mapValueToFuture(Map<String, T> map, String key) {
        final T value = map.get(key);
        if (value != null) {
            return Future.succeededFuture(value);
        } else {
            return Future.failedFuture(new PreBidRequestException("Not found"));
        }
    }
}
