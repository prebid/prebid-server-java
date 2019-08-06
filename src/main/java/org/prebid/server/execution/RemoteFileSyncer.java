package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.function.Consumer;

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

    public RemoteFileSyncer(String downloadUrl, String saveFilePath, int retryCount, long retryInterval, long timeout,
                            HttpClient httpClient, Vertx vertx) {
        this.downloadUrl = HttpUtil.validateUrl(downloadUrl);
        this.saveFilePath = Objects.requireNonNull(saveFilePath);
        this.domainUrl = HttpUtil.getDomainFromUrl(downloadUrl);
        this.fileSystem = vertx.fileSystem();

        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.vertx = vertx;

        this.openOptions = new OpenOptions()
                // Will fail if file already exist setCreateNew
                .setCreateNew(true);
    }

    /**
     * Fetches remote file and executes given callback with filepath on finish.
     */
    public void syncForFilepath(Consumer<String> consumer) {
        downloadIfNotExist().setHandler(aVoid -> {
            if (aVoid.succeeded()) {
                consumer.accept(saveFilePath);
            } else { // ONLY FOR TEST
                logger.info("CANT accept fail" + aVoid.cause());
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
                future.fail(new RuntimeException("Cant check existence of a file: " + saveFilePath));
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

                    // Not sure about this exceptions.
                } catch (Exception e) {
                    future.fail(new RuntimeException(String.format("Error while resolving url: %s", downloadUrl), e));
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
                //TODO Mb there are smt like httpClentResponse . close ? (Warring about memory leak)
                if (!future.isComplete()) {
                    future.fail(new RuntimeException("Timeout on download"));
                }
            });

            //TODO need pump.stop, or it already handled (Warring about memory leak)
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

