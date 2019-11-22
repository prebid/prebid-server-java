package org.prebid.server.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
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
    private final int retryCount; // how many times try to download
    private final long retryInterval; // how long to wait between failed retries
    private final long timeout;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final FileSystem fileSystem;

    private RemoteFileSyncer(String downloadUrl, String saveFilePath, int retryCount,
                             long retryInterval, long timeout, HttpClient httpClient, Vertx vertx,
                             FileSystem fileSystem) {
        this.downloadUrl = downloadUrl;
        this.saveFilePath = saveFilePath;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.vertx = vertx;
        this.fileSystem = fileSystem;
    }

    public static RemoteFileSyncer create(String downloadUrl, String saveFilePath, int retryCount, long retryInterval,
                                          long timeout, HttpClient httpClient, Vertx vertx) {
        HttpUtil.validateUrl(downloadUrl);
        Objects.requireNonNull(saveFilePath);
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(httpClient);
        final FileSystem fileSystem = vertx.fileSystem();

        createAndCheckWritePermissionsFor(fileSystem, saveFilePath);

        return new RemoteFileSyncer(downloadUrl, saveFilePath, retryCount, retryInterval, timeout, httpClient, vertx,
                fileSystem);
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

    private Future<Void> downloadIfNotExist(RemoteFileProcessor fileProcessor) {
        final Promise<Void> promise = Promise.promise();
        fileSystem.exists(saveFilePath, existResult -> handleFileExisting(promise, existResult, fileProcessor));
        return promise.future();
    }

    private void handleFileExisting(Promise<Void> promise, AsyncResult<Boolean> existResult,
                                    RemoteFileProcessor fileProcessor) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileProcessor.setDataPath(saveFilePath)
                        .setHandler(serviceRespond -> handleServiceRespond(serviceRespond, promise));
            } else {
                tryDownload(promise);
            }
        } else {
            promise.fail(existResult.cause());
        }
    }

    private void handleServiceRespond(AsyncResult<?> processResult, Promise<Void> promise) {
        if (processResult.failed()) {
            final Throwable cause = processResult.cause();
            cleanUp().setHandler(removalResult -> handleCorruptedFileRemoval(removalResult, promise, cause));
        } else {
            logger.info("Existing file {0} was successfully reused for service", saveFilePath);
        }
    }

    private void handleCorruptedFileRemoval(
            AsyncResult<Void> removalResult, Promise<Void> promise, Throwable serviceCause) {

        if (removalResult.failed()) {
            final Throwable cause = removalResult.cause();
            promise.fail(new PreBidException(
                    String.format("Corrupted file %s cant be deleted. Please check permission or delete manually.",
                            saveFilePath), cause));
        } else {
            logger.info("Existing file {0} cant be processed by service, try to download after removal",
                    serviceCause, saveFilePath);

            tryDownload(promise);
        }
    }

    private void tryDownload(Promise<Void> promise) {
        download().setHandler(downloadResult -> handleDownload(promise, downloadResult));
    }

    private Future<Void> download() {
        final Promise<Void> promise = Promise.promise();
        final OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        fileSystem.open(saveFilePath, openOptions, openResult -> handleFileOpenWithDownload(promise, openResult));
        return promise.future();
    }

    private void handleFileOpenWithDownload(Promise<Void> promise, AsyncResult<AsyncFile> openResult) {
        if (openResult.succeeded()) {
            final AsyncFile asyncFile = openResult.result();
            try {
                // .getNow is not working
                final HttpClientRequest httpClientRequest = httpClient
                        .getAbs(downloadUrl, response -> pumpFileFromRequest(response, asyncFile, promise));
                httpClientRequest.end();
            } catch (Exception e) {
                promise.fail(e);
            }
        } else {
            promise.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(
            HttpClientResponse httpClientResponse, AsyncFile asyncFile, Promise<Void> promise) {

        logger.info("Trying to download from {0}", downloadUrl);
        httpClientResponse.pause();
        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();
        httpClientResponse.resume();

        final long idTimer = setTimeoutTimer(asyncFile, promise, pump);

        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, promise, idTimer));
    }

    private long setTimeoutTimer(AsyncFile asyncFile, Promise<Void> promise, Pump pump) {
        return vertx.setTimer(timeout, timerId -> handleTimeout(asyncFile, promise, pump));
    }

    private void handleTimeout(AsyncFile asyncFile, Promise<Void> promise, Pump pump) {
        pump.stop();
        asyncFile.close();
        if (!promise.future().isComplete()) {
            promise.fail(new TimeoutException("Timeout on download"));
        }
    }

    private void handleResponseEnd(AsyncFile asyncFile, Promise<Void> promise, long idTimer) {
        vertx.cancelTimer(idTimer);
        asyncFile.flush().close(promise);
    }

    private void handleDownload(Promise<Void> promise, AsyncResult<Void> downloadResult) {
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
            cleanUp().compose(aVoid -> download())
                    .setHandler(retryResult -> handleRetryResult(receivedPromise, retryInterval, next, retryResult));
        } else {
            cleanUp().setHandler(aVoid -> receivedPromise.fail(new PreBidException("File sync failed after retries")));
        }
    }

    private Future<Void> cleanUp() {
        final Promise<Void> promise = Promise.promise();
        fileSystem.exists(saveFilePath, existResult -> handleFileExistsWithDelete(promise, existResult));
        return promise.future();
    }

    private void handleFileExistsWithDelete(Promise<Void> promise, AsyncResult<Boolean> existResult) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileSystem.delete(saveFilePath, promise);
            } else {
                promise.complete();
            }
        } else {
            promise.fail(new PreBidException(String.format("Cant check if file exists %s", saveFilePath)));
        }
    }

    private void handleRetryResult(
            Promise<Void> promise, long retryInterval, long next, AsyncResult<Void> retryResult) {

        if (retryResult.succeeded()) {
            promise.complete();
        } else {
            retryDownload(promise, retryInterval, next);
        }
    }

    private void handleSync(RemoteFileProcessor remoteFileProcessor, AsyncResult<Void> syncResult) {
        if (syncResult.succeeded()) {
            remoteFileProcessor.setDataPath(saveFilePath)
                    .setHandler(this::logFileProcessStatus);
        } else {
            logger.error("Cant download file from {0}", syncResult.cause(), downloadUrl);
        }
    }

    private void logFileProcessStatus(AsyncResult<?> serviceRespond) {
        if (serviceRespond.succeeded()) {
            logger.info("Service successfully receive file {0}.", saveFilePath);
        } else {
            logger.error("Service cant process file {0} and still unavailable.", saveFilePath);
        }
    }
}

