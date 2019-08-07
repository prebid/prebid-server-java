package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncer {
    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSyncer.class);

    private final String downloadUrl;  // url to resource to be downloaded
    private final String domainUrl;
    private final String saveFilePath; // full path on file system where downloaded file located
    private final int retryCount; // how many times try to download
    private final long retryInterval; // how long to wait between failed retries
    private final long timeout;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final FileSystem fileSystem;
    private final OpenOptions openOptions;

    private RemoteFileSyncer(String downloadUrl, String domainUrl, String saveFilePath, int retryCount,
                             long retryInterval, long timeout, HttpClient httpClient, Vertx vertx,
                             FileSystem fileSystem, OpenOptions openOptions) {
        this.downloadUrl = downloadUrl;
        this.domainUrl = domainUrl;
        this.saveFilePath = saveFilePath;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.vertx = vertx;
        this.fileSystem = fileSystem;
        this.openOptions = openOptions;
    }

    public static RemoteFileSyncer create(String downloadUrl, String saveFilePath, int retryCount, long retryInterval,
                                          long timeout, HttpClient httpClient, Vertx vertx) {
        HttpUtil.validateUrl(downloadUrl);
        String domainFromUrl = HttpUtil.getDomainFromUrl(downloadUrl);
        Objects.requireNonNull(domainFromUrl);
        Objects.requireNonNull(saveFilePath);
        Objects.requireNonNull(vertx);
        FileSystem fileSystem = vertx.fileSystem();
        Objects.requireNonNull(httpClient);
        validateRetryCount(retryCount);
        validateRetryInterval(retryInterval);
        validateTimeout(timeout);

        createAndCheckWritePermissionsFor(fileSystem, saveFilePath);

        // Will fail if file already exist setCreateNew
        OpenOptions openOptions = new OpenOptions().setCreateNew(true);
        return new RemoteFileSyncer(downloadUrl, domainFromUrl, saveFilePath, retryCount, retryInterval, timeout,
                httpClient, vertx, fileSystem, openOptions);
    }

    private static void validateTimeout(long timeout) {
        validate(timeout, l -> l < 1000,
                String.format("Timeout need to be grater than 1000 ms, current value: %s", timeout));
    }

    private static void validateRetryInterval(long retryInterval) {
        validate(retryInterval, l -> l < 100,
                String.format("RetryInterval need to be grater than 100 ms, current value: %s", retryInterval));
    }

    private static void validateRetryCount(int retryCount) {
        validate(retryCount, l -> l < 0,
                String.format("RetryCount need to be grater than 0, current value: %s", retryCount));
    }

    private static <T> void validate(T value, Predicate<T> violation, String errorMessage) {
        if (violation.test(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
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
        downloadIfNotExist().setHandler(aVoid -> {
            if (aVoid.succeeded()) {
                consumer.accept(saveFilePath);
            } else { // ONLY FOR TEST
                logger.info(String.format("Consumer not accept anything for file: %s, with cause: %s", saveFilePath,
                        aVoid.cause()));
            }
        });
    }

    private Future<Void> downloadIfNotExist() {
        Future<Void> future = Future.future();
        fileSystem.exists(saveFilePath, existResult -> {
            if (existResult.succeeded()) {
                if (existResult.result()) {
                    future.complete();
                } else {
                    tryDownload(future);
                }
            } else {
                future.fail(existResult.cause());
            }
        });
        return future;
    }

    private void tryDownload(Future<Void> future) {
        download().setHandler(event -> {
            if (event.failed()) {
                retryDownload(future, retryInterval, retryCount);
            } else {
                future.complete();
            }
        });
    }

    private Future<Void> download() {
        final Future<Void> future = Future.future();
        fileSystem.open(saveFilePath, openOptions, fileOpenResult -> {
            if (fileOpenResult.succeeded()) {
                AsyncFile asyncFile = fileOpenResult.result();
                try {
                    // .getNow is not working
                    HttpClientRequest httpClientRequest = httpClient
                            .get(domainUrl, downloadUrl, pumpFileFromRequest(asyncFile, future));
                    httpClientRequest.end();
                } catch (Exception ex) {
                    future.fail(ex);
                }
            } else {
                future.fail(fileOpenResult.cause());
            }
        });
        return future;
    }

    private Handler<HttpClientResponse> pumpFileFromRequest(AsyncFile asyncFile, Future<Void> future) {
        return httpClientResponse -> {
            logger.info("RESPONSE WAS GIVEN");
            httpClientResponse.pause();
            Pump pump = Pump.pump(httpClientResponse, asyncFile);
            pump.start();
            httpClientResponse.resume();

            final long idTimer = vertx.setTimer(timeout, event -> {
                logger.info("TIMEOUT ON DOWNLOAD");
                pump.stop();
                asyncFile.close();
                //TODO Mb there are smt like httpClentResponse. close ? (Warring about memory leak)
                if (!future.isComplete()) {
                    future.fail(new TimeoutException("Timeout on download"));
                }
            });

            httpClientResponse.endHandler(responseEndResult -> {
                logger.info("END HANDLER");
                vertx.cancelTimer(idTimer);
                asyncFile.flush().close(future);
            });
        };
    }

    private void retryDownload(Future<Void> receivedFuture, long retryInterval, long retryCount) {
        logger.info("RETRY " + retryCount);
        vertx.setTimer(retryInterval, retryTimerId -> {
            if (retryCount > 0) {
                final long next = retryCount - 1;
                cleanUp().compose(aVoid -> download())
                        .setHandler(downloadResult -> {
                            if (downloadResult.succeeded()) {
                                receivedFuture.complete();
                            } else {
                                logger.info("FAILED after retry");
                                retryDownload(receivedFuture, retryInterval, next);
                            }
                        });
            } else {
                logger.info("FINAL FAIL");
                // TODO Mb we can use also timeout, but service already responding with emptyObject,
                // and it can get only fail
                cleanUp().setHandler(aVoid -> receivedFuture.fail(new RuntimeException()));
            }
        });
    }

    private Future<Void> cleanUp() {
        logger.info("CLEANUP");
        final Future<Void> future = Future.future();
        fileSystem.exists(saveFilePath, existResult -> {
            if (existResult.succeeded()) {
                if (existResult.result()) {
                    fileSystem.delete(saveFilePath, future);
                } else {
                    logger.info("CLEANUP Future complete");
                    future.complete();
                }
            } else {
                future.fail(new RuntimeException("Cant check if file exists " + saveFilePath));
            }
        });
        return future;
    }
}

