package org.rtb.vexing.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private Config() {
    }

    public static Future<JsonObject> resolve(Vertx vertx, String defaultFile) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(defaultFile);

        // read configuration in the next order:
        // 1. default configuration in the jar file
        // 2. verticle configuration (normally it should be a external config provided with -conf option on startup)
        // 3. system properties
        // 4. environment variables
        final ConfigRetrieverOptions options = new ConfigRetrieverOptions();

        final String defaultConfig = readFromClasspath(defaultFile);
        if (!Objects.toString(defaultConfig, "").isEmpty()) {
            options.addStore(new ConfigStoreOptions().setType("json").setConfig(new JsonObject(defaultConfig)));
        }

        options.addStore(new ConfigStoreOptions().setType("json").setConfig(vertx.getOrCreateContext().config()))
                .addStore(new ConfigStoreOptions().setType("sys"))
                .addStore(new ConfigStoreOptions().setType("env"));

        final Future<JsonObject> config = ConfigRetriever.getConfigAsFuture(ConfigRetriever.create(vertx, options));

        return config.compose(c -> Future.succeededFuture(flatten(c)));
    }

    private static String readFromClasspath(String path) {
        String content = null;

        final InputStream resourceAsStream = Config.class.getResourceAsStream(path);
        if (resourceAsStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                    StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            } catch (UncheckedIOException | IOException | DecodeException e) {
                logger.warn("Could not read resource with path from {0} from classpath", path, e);
            }
        }

        return content;
    }

    private static JsonObject flatten(JsonObject config) {
        final JsonObject flatConfig = new JsonObject();

        // 1. add flattened composite parameters
        config.stream()
                .filter(entry -> isComposite(entry.getValue()))
                .flatMap(entry ->
                        flatten(Collections.singletonList(entry.getKey()), config.getJsonObject(entry.getKey())))
                .forEach(flatConfig::mergeIn);

        // 2. add top level flat parameters so that they override flattened composite ones, otherwise it wouldn't be
        // possible to override composite values via system properties and environment variables
        config.stream()
                .filter(entry -> !isComposite(entry.getValue()))
                .forEach(entry -> flatConfig.put(entry.getKey(), entry.getValue()));

        return flatConfig;
    }

    private static Stream<JsonObject> flatten(List<String> path, JsonObject subConfig) {
        final Stream<JsonObject> flatNodes = subConfig.stream()
                .filter(entry -> isComposite(entry.getValue()))
                .flatMap(entry -> flatten(append(path, entry.getKey()), subConfig.getJsonObject(entry.getKey())));

        final Stream<JsonObject> flatLeaves = subConfig.stream()
                .filter(entry -> !isComposite(entry.getValue()))
                .map(entry -> new JsonObject().put(pathToString(append(path, entry.getKey())), entry.getValue()));

        return Stream.concat(flatNodes, flatLeaves);
    }

    private static boolean isComposite(Object value) {
        return value instanceof JsonObject || value instanceof Map;
    }

    private static <T> List<T> append(List<T> list, T value) {
        final List<T> newList = new ArrayList<>(list);
        newList.add(value);
        return newList;
    }

    private static String pathToString(List<String> path) {
        return String.join(".", path.toArray(new String[path.size()]));
    }
}
