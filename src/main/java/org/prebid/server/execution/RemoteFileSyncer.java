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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncer {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSyncer.class);

    private final String downloadUrl;  // url to resource to be downloaded
    private final String saveFilePath; // full path on file system where downloaded file located
    private final String tmpFilePath; // full path on file system where tmp file located
    private final int retryCount; // how many times try to download
    private final long retryInterval; // how long to wait between failed retries
    private final long timeout;
    private final long updatePeriod;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final FileSystem fileSystem;

    private RemoteFileSyncer(String downloadUrl, String saveFilePath, String tmpFilePath, int retryCount,
                             long retryInterval, long timeout, long updatePeriod, HttpClient httpClient, Vertx vertx,
                             FileSystem fileSystem) {
        this.downloadUrl = downloadUrl;
        this.saveFilePath = saveFilePath;
        this.tmpFilePath = tmpFilePath;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.timeout = timeout;
        this.updatePeriod = updatePeriod;
        this.httpClient = httpClient;
        this.vertx = vertx;
        this.fileSystem = fileSystem;
    }

    public static RemoteFileSyncer create(String downloadUrl, String saveFilePath, String tmpFilePath, int retryCount,
                                          long retryInterval, long timeout, long updatePeriod, HttpClient httpClient,
                                          Vertx vertx, FileSystem fileSystem) {
        HttpUtil.validateUrl(downloadUrl);
        Objects.requireNonNull(saveFilePath);
        Objects.requireNonNull(tmpFilePath);
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(fileSystem);

        createAndCheckWritePermissionsFor(fileSystem, saveFilePath);
        createAndCheckWritePermissionsFor(fileSystem, tmpFilePath);

        return new RemoteFileSyncer(downloadUrl, saveFilePath, tmpFilePath, retryCount, retryInterval, timeout,
                updatePeriod, httpClient, vertx, fileSystem);
    }

    /**
     * Creates if doesn't exists and checks write permissions for the given directory.
     */
    private static void createAndCheckWritePermissionsFor(FileSystem fileSystem, String filePath) {
        try {
            final String dirPath = Paths.get(filePath).getParent().toString();
            final FileProps props = fileSystem.existsBlocking(dirPath) ? fileSystem.propsBlocking(dirPath) : null;
            if (props == null || !props.isDirectory()) {
                fileSystem.mkdirsBlocking(dirPath);
            } else if (!Files.isWritable(Paths.get(dirPath))) {
                throw new PreBidException(String.format("No write permissions for directory: %s", dirPath));
            }
        } catch (FileSystemException | InvalidPathException e) {
            throw new PreBidException(String.format("Cannot create directory for file: %s", filePath), e);
        }
    }

    /**
     * Fetches remote file and executes given callback with filepath on finish.
     */
    public void syncForFilepath(RemoteFileProcessor remoteFileProcessor) {
        downloadIfNotExist(remoteFileProcessor).onComplete(syncResult -> handleSync(remoteFileProcessor, syncResult));
    }

    private Future<Boolean> downloadIfNotExist(RemoteFileProcessor fileProcessor) {
        final Promise<Boolean> promise = Promise.promise();
        checkFileExist(saveFilePath).onComplete(existResult ->
                handleFileExistingWithSync(existResult, fileProcessor, promise));
        return promise.future();
    }

    private Future<Boolean> checkFileExist(String filePath) {
        final Promise<Boolean> promise = Promise.promise();
        fileSystem.exists(filePath, async -> {
            if (async.succeeded()) {
                promise.complete(async.result());
            } else {
                promise.fail(String.format("Cant check if file exists %s", filePath));
            }
        });
        return promise.future();
    }

    private void handleFileExistingWithSync(AsyncResult<Boolean> existResult, RemoteFileProcessor fileProcessor,
                                            Promise<Boolean> promise) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileProcessor.setDataPath(saveFilePath)
                        .onComplete(serviceRespond -> handleServiceRespond(serviceRespond, promise));
            } else {
                syncRemoteFiles().onComplete(promise);
            }
        } else {
            promise.fail(existResult.cause());
        }
    }

    private void handleServiceRespond(AsyncResult<?> processResult, Promise<Boolean> promise) {
        if (processResult.failed()) {
            final Throwable cause = processResult.cause();
            cleanUp(saveFilePath).onComplete(removalResult -> handleCorruptedFileRemoval(removalResult, promise,
                    cause));
        } else {
            promise.complete(false);
            logger.info("Existing file {0} was successfully reused for service", saveFilePath);
        }
    }

    private Future<Void> cleanUp(String filePath) {
        final Promise<Void> promise = Promise.promise();
        checkFileExist(filePath).onComplete(existResult -> handleFileExistsWithDelete(filePath, existResult, promise));
        return promise.future();
    }

    private void handleFileExistsWithDelete(String filePath, AsyncResult<Boolean> existResult, Promise<Void> promise) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileSystem.delete(filePath, promise);
            } else {
                promise.complete();
            }
        } else {
            promise.fail(new PreBidException(String.format("Cant check if file exists %s", filePath)));
        }
    }

    private void handleCorruptedFileRemoval(
            AsyncResult<Void> removalResult, Promise<Boolean> promise, Throwable serviceCause) {
        if (removalResult.failed()) {
            final Throwable cause = removalResult.cause();
            promise.fail(new PreBidException(
                    String.format("Corrupted file %s cant be deleted. Please check permission or delete manually.",
                            saveFilePath), cause));
        } else {
            logger.info("Existing file {0} cant be processed by service, try to download after removal",
                    serviceCause, saveFilePath);

            syncRemoteFiles().onComplete(promise);
        }
    }

    private Future<Boolean> syncRemoteFiles() {
        return tryDownload()
                .compose(downloadResult -> swapFiles())
                .map(true);
    }

    private Future<Void> tryDownload() {
        final Promise<Void> promise = Promise.promise();
        cleanUp(tmpFilePath).onComplete(event -> handleTmpDelete(event, promise));
        return promise.future();
    }

    private void handleTmpDelete(AsyncResult<Void> tmpDeleteResult, Promise<Void> promise) {
        if (tmpDeleteResult.failed()) {
            promise.fail(tmpDeleteResult.cause());
        } else {
            download().onComplete(downloadResult -> handleDownload(downloadResult, promise));
        }
    }

    private Future<Void> download() {
        final Promise<Void> promise = Promise.promise();
        final OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        fileSystem.open(tmpFilePath, openOptions, openResult -> handleFileOpenWithDownload(openResult, promise));
        return promise.future();
    }

    private void handleFileOpenWithDownload(AsyncResult<AsyncFile> openResult, Promise<Void> promise) {
        if (openResult.succeeded()) {
            final AsyncFile asyncFile = openResult.result();
            try {
                httpClient.getAbs(downloadUrl, response -> pumpFileFromRequest(response, asyncFile, promise)).end();
            } catch (Exception e) {
                promise.fail(e);
            }
        } else {
            promise.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(
            HttpClientResponse httpClientResponse, AsyncFile asyncFile, Promise<Void> promise) {

        logger.info("Trying to download file from {0}", downloadUrl);
        httpClientResponse.pause();
        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();
        httpClientResponse.resume();

        final long idTimer = setTimeoutTimer(asyncFile, pump, promise);

        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, idTimer, promise));
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

    private void handleDownload(AsyncResult<Void> downloadResult, Promise<Void> promise) {
        if (downloadResult.failed()) {
            retryDownload(promise, retryInterval, retryCount);
        } else {
            promise.complete();
        }
    }

    private void retryDownload(Promise<Void> receivedPromise, long retryInterval, long retryCount) {
        logger.info("Set retry {0} to download from {1}. {2} retries left", retryInterval, downloadUrl, retryCount);
        vertx.setTimer(retryInterval, retryTimerId -> handleRetry(receivedPromise, retryInterval, retryCount));
    }

    private void handleRetry(Promise<Void> receivedPromise, long retryInterval, long retryCount) {
        if (retryCount > 0) {
            final long next = retryCount - 1;
            cleanUp(tmpFilePath).compose(ignore -> download())
                    .onComplete(retryResult -> handleRetryResult(retryInterval, next, retryResult, receivedPromise));
        } else {
            cleanUp(tmpFilePath).onComplete(ignore -> receivedPromise.fail(new PreBidException(
                    String.format("File sync failed after %s retries", this.retryCount - retryCount))));
        }
    }

    private void handleRetryResult(long retryInterval, long next, AsyncResult<Void> retryResult,
                                   Promise<Void> promise) {
        if (retryResult.succeeded()) {
            promise.complete();
        } else {
            retryDownload(promise, retryInterval, next);
        }
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
            logger.info("Service successfully receive file {0}.", saveFilePath);
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
        return checkFileExist(saveFilePath)
                .compose(fileExists -> fileExists ? isNeedToUpdate() : Future.succeededFuture(true))
                .compose(needUpdate -> needUpdate ? syncRemoteFiles() : Future.succeededFuture(false));
    }

    private Future<Boolean> isNeedToUpdate() {
        final Promise<Boolean> isNeedToUpdate = Promise.promise();
        httpClient.headAbs(downloadUrl, response -> checkNewVersion(response, isNeedToUpdate))
                .exceptionHandler(isNeedToUpdate::fail)
                .end();
        return isNeedToUpdate.future();
    }

    private void checkNewVersion(HttpClientResponse response, Promise<Boolean> isNeedToUpdate) {
        final String contentLengthParameter = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (StringUtils.isNumeric(contentLengthParameter) && !contentLengthParameter.equals("0")) {
            final long contentLength = Long.parseLong(contentLengthParameter);
            fileSystem.props(saveFilePath, filePropsResult -> {
                if (filePropsResult.succeeded()) {
                    isNeedToUpdate.complete(filePropsResult.result().size() != contentLength);
                } else {
                    isNeedToUpdate.fail(filePropsResult.cause());
                }
            });
        } else {
            isNeedToUpdate.fail(String.format("ContentLength is invalid: %s", contentLengthParameter));
        }
    }
}

