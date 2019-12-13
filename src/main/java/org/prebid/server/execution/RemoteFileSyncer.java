package org.prebid.server.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
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
        final Future<Void> future = Future.future();
        fileSystem.exists(saveFilePath, existResult -> handleFileExisting(future, existResult, fileProcessor));
        return future;
    }

    private void handleFileExisting(Future<Void> future, AsyncResult<Boolean> existResult,
                                    RemoteFileProcessor fileProcessor) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileProcessor.setDataPath(saveFilePath)
                        .setHandler(serviceRespond -> handleServiceRespond(serviceRespond, future));
            } else {
                tryDownload(future);
            }
        } else {
            future.fail(existResult.cause());
        }
    }

    private void handleServiceRespond(AsyncResult<?> processResult, Future<Void> future) {
        if (processResult.failed()) {
            final Throwable cause = processResult.cause();
            cleanUp().setHandler(removalResult -> handleCorruptedFileRemoval(removalResult, future, cause));
        } else {
            logger.info("Existing file {0} was successfully reused for service", saveFilePath);
        }
    }

    private void handleCorruptedFileRemoval(AsyncResult<Void> removalResult, Future<Void> future,
                                            Throwable serviceCause) {
        if (removalResult.failed()) {
            final Throwable cause = removalResult.cause();
            future.fail(new PreBidException(
                    String.format("Corrupted file %s cant be deleted. Please check permission or delete manually.",
                            saveFilePath), cause));
        } else {
            logger.info("Existing file {0} cant be processed by service, try to download after removal",
                    serviceCause, saveFilePath);

            tryDownload(future);
        }
    }

    private void tryDownload(Future<Void> future) {
        download().setHandler(downloadResult -> handleDownload(future, downloadResult));
    }

    private Future<Void> download() {
        final Future<Void> future = Future.future();
        final OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        fileSystem.open(saveFilePath, openOptions, openResult -> handleFileOpenWithDownload(future, openResult));
        return future;
    }

    private void handleFileOpenWithDownload(Future<Void> future, AsyncResult<AsyncFile> openResult) {
        if (openResult.succeeded()) {
            final AsyncFile asyncFile = openResult.result();
            try {
                // .getNow is not working
                final HttpClientRequest httpClientRequest = httpClient
                        .getAbs(downloadUrl, response -> pumpFileFromRequest(response, asyncFile, future));
                httpClientRequest.end();
            } catch (Exception e) {
                future.fail(e);
            }
        } else {
            future.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(HttpClientResponse httpClientResponse, AsyncFile asyncFile, Future<Void> future) {
        logger.info("Trying to download from {0}", downloadUrl);
        httpClientResponse.pause();
        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();
        httpClientResponse.resume();

        final long idTimer = setTimeoutTimer(asyncFile, future, pump);

        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, future, idTimer));
    }

    private long setTimeoutTimer(AsyncFile asyncFile, Future<Void> future, Pump pump) {
        return vertx.setTimer(timeout, timerId -> handleTimeout(asyncFile, future, pump));
    }

    private void handleTimeout(AsyncFile asyncFile, Future<Void> future, Pump pump) {
        pump.stop();
        asyncFile.close();
        if (!future.isComplete()) {
            future.fail(new TimeoutException("Timeout on download"));
        }
    }

    private void handleResponseEnd(AsyncFile asyncFile, Future<Void> future, long idTimer) {
        vertx.cancelTimer(idTimer);
        asyncFile.flush().close(future);
    }

    private void handleDownload(Future<Void> future, AsyncResult<Void> downloadResult) {
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
            cleanUp().compose(aVoid -> download())
                    .setHandler(retryResult -> handleRetryResult(receivedFuture, retryInterval, next, retryResult));
        } else {
            cleanUp().setHandler(aVoid -> receivedFuture.fail(new PreBidException("File sync failed after retries")));
        }
    }

    private Future<Void> cleanUp() {
        final Future<Void> future = Future.future();
        fileSystem.exists(saveFilePath, existResult -> handleFileExistsWithDelete(future, existResult));
        return future;
    }

    private void handleFileExistsWithDelete(Future<Void> future, AsyncResult<Boolean> existResult) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                fileSystem.delete(saveFilePath, future);
            } else {
                future.complete();
            }
        } else {
            future.fail(new PreBidException(String.format("Cant check if file exists %s", saveFilePath)));
        }
    }

    private void handleRetryResult(Future<Void> future, long retryInterval, long next, AsyncResult<Void> retryResult) {
        if (retryResult.succeeded()) {
            future.complete();
        } else {
            retryDownload(future, retryInterval, next);
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

