package org.prebid.server.execution.file;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.http.HttpClientOptions;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.file.syncer.FileSyncer;
import org.prebid.server.execution.file.syncer.LocalFileSyncer;
import org.prebid.server.execution.file.syncer.RemoteFileSyncerV2;
import org.prebid.server.execution.retry.ExponentialBackoffRetryPolicy;
import org.prebid.server.execution.retry.FixedIntervalRetryPolicy;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.spring.config.model.ExponentialBackoffProperties;
import org.prebid.server.spring.config.model.FileSyncerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {

    private FileUtil() {
    }

    public static void createAndCheckWritePermissionsFor(FileSystem fileSystem, String filePath) {
        try {
            final Path dirPath = Paths.get(filePath).getParent();
            final String dirPathString = dirPath.toString();
            final FileProps props = fileSystem.existsBlocking(dirPathString)
                    ? fileSystem.propsBlocking(dirPathString)
                    : null;

            if (props == null || !props.isDirectory()) {
                fileSystem.mkdirsBlocking(dirPathString);
            } else if (!Files.isWritable(dirPath)) {
                throw new PreBidException("No write permissions for directory: " + dirPath);
            }
        } catch (FileSystemException | InvalidPathException e) {
            throw new PreBidException("Cannot create directory for file: " + filePath, e);
        }
    }

    public static FileSyncer fileSyncerFor(FileProcessor fileProcessor,
                                           FileSyncerProperties properties,
                                           Vertx vertx) {

        return switch (properties.getType()) {
            case LOCAL -> new LocalFileSyncer(
                    fileProcessor,
                    properties.getSaveFilepath(),
                    properties.getUpdateIntervalMs(),
                    toRetryPolicy(properties),
                    vertx);
            case REMOTE -> remoteFileSyncer(fileProcessor, properties, vertx);
        };
    }

    private static RemoteFileSyncerV2 remoteFileSyncer(FileProcessor fileProcessor,
                                                       FileSyncerProperties properties,
                                                       Vertx vertx) {

        final HttpClientProperties httpClientProperties = properties.getHttpClient();
        final HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                .setMaxRedirects(httpClientProperties.getMaxRedirects());

        return new RemoteFileSyncerV2(
                fileProcessor,
                properties.getDownloadUrl(),
                properties.getSaveFilepath(),
                properties.getTmpFilepath(),
                vertx.createHttpClient(httpClientOptions),
                properties.getTimeoutMs(),
                properties.isCheckSize(),
                properties.getUpdateIntervalMs(),
                toRetryPolicy(properties),
                vertx);
    }

    // TODO: remove after transition period
    private static RetryPolicy toRetryPolicy(FileSyncerProperties properties) {
        final Long retryIntervalMs = properties.getRetryIntervalMs();
        final Integer retryCount = properties.getRetryCount();
        final boolean fixedRetryPolicyDefined = ObjectUtils.anyNotNull(retryIntervalMs, retryCount);
        final boolean fixedRetryPolicyValid = ObjectUtils.allNotNull(retryIntervalMs, retryCount)
                || !fixedRetryPolicyDefined;

        if (!fixedRetryPolicyValid) {
            throw new IllegalArgumentException("fixed interval retry policy is invalid");
        }

        final ExponentialBackoffProperties exponentialBackoffProperties = properties.getRetry();
        return fixedRetryPolicyDefined
                ? FixedIntervalRetryPolicy.limited(retryIntervalMs, retryCount)
                : ExponentialBackoffRetryPolicy.of(
                exponentialBackoffProperties.getDelayMillis(),
                exponentialBackoffProperties.getMaxDelayMillis(),
                exponentialBackoffProperties.getFactor(),
                exponentialBackoffProperties.getJitter());
    }
}
