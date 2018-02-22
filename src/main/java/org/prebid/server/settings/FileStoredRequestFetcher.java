package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.StoredRequestResult;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes stored requests fetching from filesystem source.
 * Immediately loads stored request data from local files. These are stored in memory for low-latency reads.
 * This expects each file in the directory to be named "{config_id}.json".
 */
public class FileStoredRequestFetcher implements StoredRequestFetcher {

    private static final String JSON_SUFFIX = ".json";

    private final Map<String, String> storedRequests;

    private FileStoredRequestFetcher(Map<String, String> storedRequests) {
        this.storedRequests = Objects.requireNonNull(storedRequests);
    }

    /**
     * Creates {@link FileStoredRequestFetcher} instance by looking for .json file extension and creates
     * {@link Map} file names without .json extension to file content. Returns an instance of
     * {@link FileStoredRequestFetcher}
     */
    public static FileStoredRequestFetcher create(String requestConfigPath, FileSystem fileSystem) {
        Objects.requireNonNull(requestConfigPath);
        Objects.requireNonNull(fileSystem);
        final List<String> filesPaths = fileSystem.readDirBlocking(requestConfigPath);
        final Map<String, String> storedRequests = filesPaths.stream()
                .filter(filepath -> filepath.endsWith(JSON_SUFFIX))
                .collect(Collectors.toMap(filepath -> StringUtils.removeEnd(new File(filepath).getName(), JSON_SUFFIX),
                        filename -> fileSystem.readFileBlocking(filename).toString()));
        return new FileStoredRequestFetcher(storedRequests);
    }

    /**
     * Creates {@link StoredRequestResult} by checking if any ids are missed in storedRequest map and adding an error
     * to list for each missed Id. Returns {@link Future<StoredRequestResult>} with all loaded files and errors list.
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout) {
        Objects.requireNonNull(ids);

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
}
