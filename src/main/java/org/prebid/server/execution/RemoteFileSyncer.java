package org.prebid.server.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import io.vertx.core.streams.Pump;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.retry.Retryable;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.util.HttpUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class RemoteFileSyncer {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSyncer.class);

    private final String downloadUrl;
    private final String saveFilePath;
    private final String tmpFilePath;
    private final RetryPolicy retryPolicy;
    private final long timeout;
    private final long updatePeriod;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final FileSystem fileSystem;

    public RemoteFileSyncer(String downloadUrl,
                            String saveFilePath,
                            String tmpFilePath,
                            RetryPolicy retryPolicy,
                            long timeout,
                            long updatePeriod,
                            HttpClient httpClient,
                            Vertx vertx) {

        this.downloadUrl = HttpUtil.validateUrl(downloadUrl);
        this.saveFilePath = Objects.requireNonNull(saveFilePath);
        this.tmpFilePath = Objects.requireNonNull(tmpFilePath);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.timeout = timeout;
        this.updatePeriod = updatePeriod;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);
        this.fileSystem = vertx.fileSystem();

        createAndCheckWritePermissionsFor(fileSystem, saveFilePath);
        createAndCheckWritePermissionsFor(fileSystem, tmpFilePath);
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

    public void sync(RemoteFileProcessor processor) {
        isFileExists(saveFilePath)
                .compose(exists -> exists ? processSavedFile(processor) : syncRemoteFiles(retryPolicy))
                .onComplete(syncResult -> handleSync(processor, syncResult));
    }

    private Future<Boolean> isFileExists(String filePath) {
        final Promise<Boolean> promise = Promise.promise();
        fileSystem.exists(filePath, async -> {
            if (async.succeeded()) {
                promise.complete(async.result());
            } else {
                promise.fail("Cant check if file exists " + filePath);
            }
        });
        return promise.future();
    }

    private Future<Boolean> processSavedFile(RemoteFileProcessor processor) {
        return processor.setDataPath(saveFilePath)
                .map(false)
                .recover(ignored -> removeCorruptedSaveFile());
    }

    private Future<Boolean> removeCorruptedSaveFile() {
        return deleteFileIfExists(saveFilePath)
                .compose(ignored -> syncRemoteFiles(retryPolicy))
                .recover(error -> Future.failedFuture(new PreBidException(
                        "Corrupted file %s can't be deleted. Please check permission or delete manually."
                                .formatted(saveFilePath), error)));
    }

    private Future<Boolean> syncRemoteFiles(RetryPolicy retryPolicy) {
        return deleteFileIfExists(tmpFilePath)
                .compose(ignored -> downloadToTempFile())
                .recover(error -> retrySync(retryPolicy))
                .compose(downloadResult -> swapFiles())
                .map(true);
    }

    private Future<Void> deleteFileIfExists(String filePath) {
        return isFileExists(filePath)
                .compose(exists -> exists ? deleteFile(filePath) : Future.succeededFuture());
    }

    private Future<Void> deleteFile(String filePath) {
        final Promise<Void> promise = Promise.promise();
        fileSystem.delete(filePath, promise);
        return promise.future();
    }

    private Future<Void> downloadToTempFile() {
        return openFile(tmpFilePath)
                .compose(tmpFile -> requestData()
                        .compose(response -> pumpToFile(response, tmpFile)));
    }

    private Future<HttpClientResponse> requestData() {
        final Promise<HttpClientResponse> promise = Promise.promise();
        httpClient.getAbs(downloadUrl, promise::complete).end();
        return promise.future();
    }

    private Future<Void> retrySync(RetryPolicy retryPolicy) {
        if (retryPolicy instanceof Retryable policy) {
            logger.info("Retrying file download from {0} with policy: {1}", downloadUrl, retryPolicy);

            final Promise<Void> promise = Promise.promise();
            vertx.setTimer(policy.delay(), timerId ->
                    syncRemoteFiles(policy.next())
                            .onFailure(promise::fail)
                            .onSuccess(ignored -> promise.complete()));

            return promise.future();
        } else {
            return Future.failedFuture(new PreBidException("File sync failed"));
        }
    }

    private Future<AsyncFile> openFile(String path) {
        final Promise<AsyncFile> promise = Promise.promise();
        fileSystem.open(path, new OpenOptions().setCreateNew(true), promise);
        return promise.future();
    }

    private Future<Void> pumpToFile(HttpClientResponse httpClientResponse, AsyncFile asyncFile) {
        final Promise<Void> promise = Promise.promise();
        logger.info("Trying to download file from {0}", downloadUrl);
        httpClientResponse.pause();

        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();

        httpClientResponse.resume();
        final long timeoutTimerId = setTimeoutTimer(asyncFile, pump, promise);
        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, timeoutTimerId, promise));

        return promise.future();
    }

    private long setTimeoutTimer(AsyncFile asyncFile, Pump pump, Promise<Void> promise) {
        return vertx.setTimer(timeout, timerId -> handleTimeout(asyncFile, pump, promise));
    }

    private void handleTimeout(AsyncFile asyncFile, Pump pump, Promise<Void> promise) {
        pump.stop();
        asyncFile.close();
        if (!promise.future().isComplete()) {
            promise.fail(new TimeoutException("Timeout on download"));
        }
    }

    private void handleResponseEnd(AsyncFile asyncFile, long idTimer, Promise<Void> promise) {
        vertx.cancelTimer(idTimer);
        asyncFile.flush().close(promise);
    }

    private Future<Void> swapFiles() {
        final Promise<Void> promise = Promise.promise();
        logger.info("Sync {0} to {1}", tmpFilePath, saveFilePath);

        final CopyOptions copyOptions = new CopyOptions().setReplaceExisting(true);
        fileSystem.move(tmpFilePath, saveFilePath, copyOptions, promise);
        return promise.future();
    }

    private void handleSync(RemoteFileProcessor remoteFileProcessor, AsyncResult<Boolean> syncResult) {
        if (syncResult.succeeded()) {
            if (syncResult.result()) {
                logger.info("Sync service for {0}", saveFilePath);
                remoteFileProcessor.setDataPath(saveFilePath)
                        .onComplete(this::logFileProcessStatus);
            } else {
                logger.info("Sync is not required for {0}", saveFilePath);
            }
        } else {
            logger.error("Cant sync file from {0}", syncResult.cause(), downloadUrl);
        }

        // setup new update regardless of the result
        if (updatePeriod > 0) {
            vertx.setTimer(updatePeriod, idUpdateNew -> configureAutoUpdates(remoteFileProcessor));
        }
    }

    private void logFileProcessStatus(AsyncResult<?> serviceRespond) {
        if (serviceRespond.succeeded()) {
            logger.info("Service successfully received file {0}.", saveFilePath);
        } else {
            logger.error("Service cant process file {0} and still unavailable.", saveFilePath);
        }
    }

    private void configureAutoUpdates(RemoteFileProcessor remoteFileProcessor) {
        logger.info("Check for updated for {0}", saveFilePath);
        tryUpdate().onComplete(asyncUpdate -> {
            if (asyncUpdate.failed()) {
                logger.warn("File {0} update failed", asyncUpdate.cause(), saveFilePath);
            }
            handleSync(remoteFileProcessor, asyncUpdate);
        });
    }

    private Future<Boolean> tryUpdate() {
        return isFileExists(saveFilePath)
                .compose(fileExists -> fileExists ? isUpdateRequired() : Future.succeededFuture(true))
                .compose(needUpdate -> needUpdate ? syncRemoteFiles(retryPolicy) : Future.succeededFuture(false));
    }

    private Future<Boolean> isUpdateRequired() {
        final Promise<Boolean> isUpdateRequired = Promise.promise();
        httpClient.headAbs(downloadUrl, response -> checkNewVersion(response, isUpdateRequired))
                .exceptionHandler(isUpdateRequired::fail)
                .end();
        return isUpdateRequired.future();
    }

    private void checkNewVersion(HttpClientResponse response, Promise<Boolean> isUpdateRequired) {
        final String contentLengthParameter = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (StringUtils.isNumeric(contentLengthParameter) && !contentLengthParameter.equals("0")) {
            final long contentLength = Long.parseLong(contentLengthParameter);
            fileSystem.props(saveFilePath, filePropsResult -> {
                if (filePropsResult.succeeded()) {
                    logger.info("Prev length = {0}, new length = {1}", filePropsResult.result().size(), contentLength);
                    isUpdateRequired.complete(filePropsResult.result().size() != contentLength);
                } else {
                    isUpdateRequired.fail(filePropsResult.cause());
                }
            });
        } else {
            isUpdateRequired.fail("ContentLength is invalid: " + contentLengthParameter);
        }
    }
}
