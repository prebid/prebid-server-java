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
import java.util.function.Consumer;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncer {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSyncer.class);

    private final String downloadUrl;  // url to resource to be downloaded
    private final String saveFilePath; // full path on file system where usable file located
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
                                          Vertx vertx) {
        HttpUtil.validateUrl(downloadUrl);
        Objects.requireNonNull(saveFilePath);
        Objects.requireNonNull(tmpFilePath);
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(httpClient);
        final FileSystem fileSystem = vertx.fileSystem();

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
    public void syncForFilepath(Consumer<String> syncConsumer) {
        checkFileExist()
                .compose(fileExists -> fileExists ? Future.succeededFuture() : syncFiles())
                .setHandler(asyncDownload -> {
                    if (asyncDownload.failed()) {
                        logger.warn("File {0} sync failed", saveFilePath, asyncDownload.cause());
                    } else {
                        syncConsumer.accept(saveFilePath);
                        logger.info("File {0} sync succeeded", saveFilePath);
                    }
                    configureAutoUpdates(syncConsumer);
                });
    }

    private void configureAutoUpdates(Consumer<String> syncConsumer) {
        if (updatePeriod > 0) {
            vertx.setTimer(updatePeriod, idUpdate -> tryUpdate().setHandler(asyncUpdate -> {
                if (asyncUpdate.failed()) {
                    logger.warn("File {0} update failed", saveFilePath, asyncUpdate.cause());
                } else {
                    if (asyncUpdate.result()) {
                        syncConsumer.accept(saveFilePath);
                        logger.info("File {0} updated", saveFilePath);
                    } else {
                        logger.info("Update is no need for {0}", saveFilePath);
                    }
                }

                // setup new update regardless of result
                vertx.setTimer(updatePeriod, idUpdateNew -> configureAutoUpdates(syncConsumer));
            }));
        }
    }

    private Future<Boolean> checkFileExist() {
        final Future<Boolean> result = Future.future();
        fileSystem.exists(saveFilePath, async -> {
            if (async.succeeded()) {
                result.complete(async.result());
            } else {
                result.fail(String.format("Cant check if file exists %s", saveFilePath));
            }
        });
        return result;
    }

    private Future<Void> syncFiles() {
        return tryDownloadWithRetries().compose(downloadResult -> swapFiles());
    }

    private Future<Void> tryDownloadWithRetries() {
        final Future<Void> result = Future.future();
        download().setHandler(downloadResult -> handleDownloadWithRetry(downloadResult, result));
        return result;
    }

    private Future<Void> swapFiles() {
        final Future<Void> result = Future.future();
        logger.info("Sync {0} to {1}", saveFilePath, tmpFilePath);

        final CopyOptions copyOptions = new CopyOptions().setReplaceExisting(true);
        fileSystem.move(tmpFilePath, saveFilePath, copyOptions, result);
        return result;
    }

    private Future<Void> download() {
        final Future<Void> result = Future.future();
        final OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        fileSystem.open(tmpFilePath, openOptions, openResult -> handleFileOpenWithDownload(openResult, result));
        return result;
    }

    private void handleFileOpenWithDownload(AsyncResult<AsyncFile> openResult, Future<Void> result) {
        if (openResult.succeeded()) {
            final AsyncFile asyncFile = openResult.result();
            try {
                httpClient.getAbs(downloadUrl, response -> pumpFileFromRequest(response, asyncFile, result))
                        .end();
            } catch (Exception ex) {
                result.fail(ex);
            }
        } else {
            result.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(HttpClientResponse httpClientResponse, AsyncFile asyncFile, Future<Void> result) {
        httpClientResponse.pause();
        final Pump pump = Pump.pump(httpClientResponse, asyncFile);
        pump.start();
        httpClientResponse.resume();
        logger.info("Downloading {0}", tmpFilePath);

        final long idTimer = setTimeoutTimer(asyncFile, result, pump);

        httpClientResponse.endHandler(responseEndResult -> handleResponseEnd(asyncFile, idTimer, result));
    }

    private long setTimeoutTimer(AsyncFile asyncFile, Future<Void> future, Pump pump) {
        return vertx.setTimer(timeout, timerId -> handleTimeout(asyncFile, future, pump));
    }

    private void handleTimeout(AsyncFile asyncFile, Future<Void> future, Pump pump) {
        pump.stop();
        asyncFile.close();
        logger.info("Timeout on download {0}", tmpFilePath);
        if (!future.isComplete()) {
            future.fail(new TimeoutException("Timeout on download"));
        }
    }

    private void handleResponseEnd(AsyncFile asyncFile, long idTimer, Future<Void> result) {
        vertx.cancelTimer(idTimer);
        asyncFile.flush().close(result);
    }

    private void handleDownloadWithRetry(AsyncResult<Void> downloadResult, Future<Void> result) {
        if (downloadResult.failed()) {
            retryDownload(retryInterval, retryCount, result);
        } else {
            result.complete();
        }
    }

    private void retryDownload(long retryInterval, long retryCount, Future<Void> receivedFuture) {
        vertx.setTimer(retryInterval, retryTimerId -> handleRetry(retryInterval, retryCount, receivedFuture));
    }

    private void handleRetry(long retryInterval, long retryCount, Future<Void> receivedFuture) {
        if (retryCount > 0) {
            final long next = retryCount - 1;
            cleanUp().compose(aVoid -> download())
                    .setHandler(retryResult -> handleRetryResult(retryResult, retryInterval, next, receivedFuture));
        } else {
            cleanUp().setHandler(aVoid -> receivedFuture.fail("File sync failed after retries"));
        }
    }

    private Future<Void> cleanUp() {
        return checkFileExist().compose(fileExists -> fileExists ? deleteTmpFile() : Future.succeededFuture());
    }

    private Future<Void> deleteTmpFile() {
        final Future<Void> result = Future.future();
        fileSystem.delete(tmpFilePath, result);
        return result;
    }

    private void handleRetryResult(AsyncResult<Void> retryResult, long retryInterval, long next, Future<Void> result) {
        if (retryResult.succeeded()) {
            result.complete();
        } else {
            retryDownload(retryInterval, next, result);
        }
    }

    private Future<Boolean> tryUpdate() {
        return checkFileExist()
                .compose(fileExists -> fileExists ? isNeedToUpdate() : Future.succeededFuture(true))
                .compose(needUpdate -> needUpdate ? syncFiles().map(true) : Future.succeededFuture(false));
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

