package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.execution.retry.Retryable;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Objects;

public class RemoteFileSyncer {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSyncer.class);

    private final RemoteFileProcessor processor;
    private final String downloadUrl;
    private final String saveFilePath;
    private final String tmpFilePath;
    private final RetryPolicy retryPolicy;
    private final long updatePeriod;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final FileSystem fileSystem;
    private final RequestOptions getFileRequestOptions;
    private final RequestOptions isUpdateRequiredRequestOptions;

    public RemoteFileSyncer(RemoteFileProcessor processor,
                            String downloadUrl,
                            String saveFilePath,
                            String tmpFilePath,
                            RetryPolicy retryPolicy,
                            long timeout,
                            long updatePeriod,
                            HttpClient httpClient,
                            Vertx vertx) {

        this.processor = Objects.requireNonNull(processor);
        this.downloadUrl = HttpUtil.validateUrl(downloadUrl);
        this.saveFilePath = Objects.requireNonNull(saveFilePath);
        this.tmpFilePath = Objects.requireNonNull(tmpFilePath);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.updatePeriod = updatePeriod;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);
        this.fileSystem = vertx.fileSystem();

        createAndCheckWritePermissionsFor(fileSystem, saveFilePath);
        createAndCheckWritePermissionsFor(fileSystem, tmpFilePath);

        getFileRequestOptions = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setTimeout(timeout)
                .setAbsoluteURI(downloadUrl);

        isUpdateRequiredRequestOptions = new RequestOptions()
                .setMethod(HttpMethod.HEAD)
                .setTimeout(timeout)
                .setAbsoluteURI(downloadUrl);
    }

    private static void createAndCheckWritePermissionsFor(FileSystem fileSystem, String filePath) {
        try {
            final String dirPath = Paths.get(filePath).getParent().toString();
            final FileProps props = fileSystem.existsBlocking(dirPath) ? fileSystem.propsBlocking(dirPath) : null;
            if (props == null || !props.isDirectory()) {
                fileSystem.mkdirsBlocking(dirPath);
            } else if (!Files.isWritable(Paths.get(dirPath))) {
                throw new PreBidException("No write permissions for directory: " + dirPath);
            }
        } catch (FileSystemException | InvalidPathException e) {
            throw new PreBidException("Cannot create directory for file: " + filePath, e);
        }
    }

    public void sync() {
        fileSystem.exists(saveFilePath)
                .compose(exists -> exists ? processSavedFile() : syncRemoteFile(retryPolicy))
                .onComplete(ignored -> setUpDeferredUpdate());
    }

    private Future<Void> processSavedFile() {
        return processor.setDataPath(saveFilePath)
                .onFailure(error -> logger.error("Can't process saved file: " + saveFilePath))
                .recover(ignored -> deleteFile(saveFilePath).mapEmpty())
                .mapEmpty();
    }

    private Future<Void> deleteFile(String filePath) {
        return fileSystem.delete(filePath)
                .onFailure(error -> logger.error("Can't delete corrupted file: " + saveFilePath));
    }

    private Future<Void> syncRemoteFile(RetryPolicy retryPolicy) {
        return fileSystem.open(tmpFilePath, new OpenOptions())

                .compose(tmpFile -> httpClient.request(getFileRequestOptions)
                        .compose(HttpClientRequest::send)
                        .compose(response -> response.pipeTo(tmpFile))
                        .onComplete(result -> tmpFile.close()))

                .compose(ignored -> fileSystem.move(
                        tmpFilePath, saveFilePath, new CopyOptions().setReplaceExisting(true)))

                .compose(ignored -> processSavedFile())
                .onFailure(ignored -> deleteFile(tmpFilePath))
                .onFailure(error -> logger.error("Could not sync remote file", error))

                .recover(error -> retrySync(retryPolicy).mapEmpty())
                .mapEmpty();

    }

    private Future<Void> retrySync(RetryPolicy retryPolicy) {
        if (retryPolicy instanceof Retryable policy) {
            logger.info("Retrying file download from {} with policy: {}", downloadUrl, retryPolicy);

            final Promise<Void> promise = Promise.promise();
            vertx.setTimer(policy.delay(), timerId -> syncRemoteFile(policy.next()).onComplete(promise));
            return promise.future();
        } else {
            return Future.failedFuture(new PreBidException("File sync failed"));
        }
    }

    private void setUpDeferredUpdate() {
        if (updatePeriod > 0) {
            vertx.setPeriodic(updatePeriod, ignored -> updateIfNeeded());
        }
    }

    private void updateIfNeeded() {
        httpClient.request(isUpdateRequiredRequestOptions)
                .compose(HttpClientRequest::send)
                .compose(response -> fileSystem.exists(saveFilePath)
                        .compose(exists -> exists
                                ? isLengthChanged(response)
                                : Future.succeededFuture(true)))
                .onSuccess(shouldUpdate -> {
                    if (shouldUpdate) {
                        syncRemoteFile(retryPolicy);
                    }
                });
    }

    private Future<Boolean> isLengthChanged(HttpClientResponse response) {
        final String contentLengthParameter = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        return StringUtils.isNumeric(contentLengthParameter) && !contentLengthParameter.equals("0")
                ? fileSystem.props(saveFilePath).map(props -> props.size() != Long.parseLong(contentLengthParameter))
                : Future.failedFuture("ContentLength is invalid: " + contentLengthParameter);
    }
}
