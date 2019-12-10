package org.prebid.server.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
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
        downloadIfNotExist(remoteFileProcessor).setHandler(syncResult -> handleSync(remoteFileProcessor, syncResult));
    }

    private Future<Boolean> downloadIfNotExist(RemoteFileProcessor fileProcessor) {
        final Future<Boolean> future = Future.future();
        checkFileExist(saveFilePath).setHandler(existResult ->
                handleFileExistingWithSync(existResult, fileProcessor, future));
        return future;
    }

    private Future<Boolean> checkFileExist(String filePath) {
        final Future<Boolean> result = Future.future();
        fileSystem.exists(filePath, async -> {
            if (async.succeeded()) {
                result.complete(async.result());
            } else {
                result.fail(String.format("Cant check if file exists %s", filePath));
            }
        });
        return result;
    }

    private void handleFileExistingWithSync(AsyncResult<Boolean> existResult, RemoteFileProcessor fileProcessor,
                                            Future<Boolean> future) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileProcessor.setDataPath(saveFilePath)
                        .setHandler(serviceRespond -> handleServiceRespond(serviceRespond, future));
            } else {
                syncRemoteFiles().setHandler(future);
            }
        } else {
            future.fail(existResult.cause());
        }
    }

    private void handleServiceRespond(AsyncResult<?> processResult, Future<Boolean> future) {
        if (processResult.failed()) {
            final Throwable cause = processResult.cause();
            cleanUp(saveFilePath).setHandler(removalResult -> handleCorruptedFileRemoval(removalResult, future, cause));
        } else {
            future.complete(false);
            logger.info("Existing file {0} was successfully reused for service", saveFilePath);
        }
    }

    private Future<Void> cleanUp(String filePath) {
        final Future<Void> future = Future.future();
        checkFileExist(filePath).setHandler(existResult -> handleFileExistsWithDelete(filePath, existResult, future));
        return future;
    }

    private void handleFileExistsWithDelete(String filePath, AsyncResult<Boolean> existResult, Future<Void> future) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileSystem.delete(filePath, future);
            } else {
                future.complete();
            }
        } else {
            future.fail(new PreBidException(String.format("Cant check if file exists %s", filePath)));
        }
    }

    private void handleCorruptedFileRemoval(AsyncResult<Void> removalResult, Future<Boolean> future,
                                            Throwable serviceCause) {
        if (removalResult.failed()) {
            final Throwable cause = removalResult.cause();
            future.fail(new PreBidException(
                    String.format("Corrupted file %s cant be deleted. Please check permission or delete manually.",
                            saveFilePath), cause));
        } else {
            logger.info("Existing file {0} cant be processed by service, try to download after removal",
                    serviceCause, saveFilePath);

            syncRemoteFiles().setHandler(future);
        }
    }

    private Future<Boolean> syncRemoteFiles() {
        return tryDownload()
                .compose(downloadResult -> swapFiles())
                .map(true);
    }

    private Future<Void> tryDownload() {
        final Future<Void> result = Future.future();
        cleanUp(tmpFilePath).setHandler(event -> handleTmpDelete(event, result));
        return result;
    }

    private void handleTmpDelete(AsyncResult<Void> tmpDeleteResult, Future<Void> result) {
        if (tmpDeleteResult.failed()) {
            result.fail(tmpDeleteResult.cause());
        } else {
            download().setHandler(downloadResult -> handleDownload(downloadResult, result));
        }
    }

    private Future<Void> download() {
        final Future<Void> future = Future.future();
        final OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        fileSystem.open(tmpFilePath, openOptions, openResult -> handleFileOpenWithDownload(openResult, future));
        return future;
    }

    private void handleFileOpenWithDownload(AsyncResult<AsyncFile> openResult, Future<Void> future) {
        if (openResult.succeeded()) {
            final AsyncFile asyncFile = openResult.result();
            try {
                httpClient.getAbs(downloadUrl, response -> pumpFileFromRequest(response, asyncFile, future)).end();
            } catch (Exception e) {
                future.fail(e);
            }
        } else {
            future.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(HttpClientResponse httpClientResponse, AsyncFile asyncFile, Future<Void> future) {
        logger.info("Trying to download file from {0}", downloadUrl);
        httpClientResponse.pause();
        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();
        httpClientResponse.resume();

        final long idTimer = setTimeoutTimer(asyncFile, pump, future);

        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, idTimer, future));
    }

    private long setTimeoutTimer(AsyncFile asyncFile, Pump pump, Future<Void> future) {
        return vertx.setTimer(timeout, timerId -> handleTimeout(asyncFile, pump, future));
    }

    private void handleTimeout(AsyncFile asyncFile, Pump pump, Future<Void> future) {
        pump.stop();
        asyncFile.close();
        if (!future.isComplete()) {
            future.fail(new TimeoutException("Timeout on download"));
        }
    }

    private void handleResponseEnd(AsyncFile asyncFile, long idTimer, Future<Void> future) {
        vertx.cancelTimer(idTimer);
        asyncFile.flush().close(future);
    }

    private void handleDownload(AsyncResult<Void> downloadResult, Future<Void> future) {
        if (downloadResult.failed()) {
            retryDownload(future, retryInterval, retryCount);
        } else {
            future.complete();
        }
    }

    private void retryDownload(Future<Void> receivedFuture, long retryInterval, long retryCount) {
        logger.info("Set retry {0} to download from {1}. {2} retries left", retryInterval, downloadUrl, retryCount);
        vertx.setTimer(retryInterval, retryTimerId -> handleRetry(receivedFuture, retryInterval, retryCount));
    }

    private void handleRetry(Future<Void> receivedFuture, long retryInterval, long retryCount) {
        if (retryCount > 0) {
            final long next = retryCount - 1;
            cleanUp(tmpFilePath).compose(ignore -> download())
                    .setHandler(retryResult -> handleRetryResult(retryInterval, next, retryResult, receivedFuture));
        } else {
            cleanUp(tmpFilePath).setHandler(ignore -> receivedFuture.fail(new PreBidException(
                    String.format("File sync failed after %s retries", this.retryCount - retryCount))));
        }
    }

    private void handleRetryResult(long retryInterval, long next, AsyncResult<Void> retryResult, Future<Void> future) {
        if (retryResult.succeeded()) {
            future.complete();
        } else {
            retryDownload(future, retryInterval, next);
        }
    }

    private Future<Void> swapFiles() {
        final Future<Void> result = Future.future();
        logger.info("Sync {0} to {1}", tmpFilePath, saveFilePath);

        final CopyOptions copyOptions = new CopyOptions().setReplaceExisting(true);
        fileSystem.move(tmpFilePath, saveFilePath, copyOptions, result);
        return result;
    }

    private void handleSync(RemoteFileProcessor remoteFileProcessor, AsyncResult<Boolean> syncResult) {
        if (syncResult.succeeded()) {
            if (syncResult.result()) {
                logger.info("Sync service for {0}", saveFilePath);
                remoteFileProcessor.setDataPath(saveFilePath)
                        .setHandler(this::logFileProcessStatus);
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
        tryUpdate().setHandler(asyncUpdate -> {
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
        final Future<Boolean> isNeedToUpdate = Future.future();
        httpClient.headAbs(downloadUrl, response -> checkNewVersion(response, isNeedToUpdate))
                .exceptionHandler(isNeedToUpdate::fail)
                .end();
        return isNeedToUpdate;
    }

    private void checkNewVersion(HttpClientResponse response, Future<Boolean> isNeedToUpdate) {
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

