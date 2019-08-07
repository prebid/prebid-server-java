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
import io.vertx.core.streams.Pump;
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

        return new RemoteFileSyncer(downloadUrl, saveFilePath, retryCount, retryInterval, timeout,
                httpClient, vertx, fileSystem);
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
    public void syncForFilepath(Consumer<String> consumer) {
        downloadIfNotExist().setHandler(aVoid -> handleSync(consumer, aVoid));
    }

    private Future<Void> downloadIfNotExist() {
        final Future<Void> future = Future.future();
        fileSystem.exists(saveFilePath, existResult -> handleFileExists(future, existResult));
        return future;
    }

    private void handleFileExists(Future<Void> future, AsyncResult<Boolean> existResult) {
        if (existResult.succeeded()) {
            if (existResult.result()) {
                future.complete();
            } else {
                tryDownload(future);
            }
        } else {
            future.fail(existResult.cause());
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
            } catch (Exception ex) {
                future.fail(ex);
            }
        } else {
            future.fail(openResult.cause());
        }
    }

    private void pumpFileFromRequest(HttpClientResponse httpClientResponse, AsyncFile asyncFile, Future<Void> future) {
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

    private void handleSync(Consumer<String> consumer, AsyncResult<Void> aVoid) {
        if (aVoid.succeeded()) {
            consumer.accept(saveFilePath);
        }
    }
}

