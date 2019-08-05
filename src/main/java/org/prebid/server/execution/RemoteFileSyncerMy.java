package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.execution.util.FileHelper;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncerMy {

    private final URL downloadUrl;  // url to resource to be downloaded

    private final Path saveFilePath; // full path on file system where downloaded file located
    private final long retryInterval; // how long to wait between failed retries
    private final long refreshPeriod; // period for file updates (0 for one time downloading)
    private final int timeout;
    private Vertx vertx;
    private FileHelper fileHelper;
    private int retryCount; // how many times try to download

    public RemoteFileSyncerMy(URL downloadUrl, Path saveFilePath, int retryCount, long retryInterval,
                              long refreshPeriod, int timeout, Vertx vertx, FileHelper fileHelper) {
        this.downloadUrl = Objects.requireNonNull(downloadUrl);
        this.saveFilePath = Objects.requireNonNull(saveFilePath);

        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.vertx = vertx;
        this.fileHelper = fileHelper;
    }

    /**
     * Fetches remote file and executes given callback with {@link InputStream} on finish.
     */
    public void syncForInputStream(Consumer<InputStream> consumer) {
        downloadIfNotExist().setHandler(event -> consumer.accept(makeInputStream()));
    }

    /**
     * Fetches remote file and executes given callback with filepath on finish.
     */
    public void syncForFilepath(Consumer<String> consumer) {
        downloadIfNotExist().setHandler(event -> consumer.accept(saveFilePath.toString()));
    }

    private Future<Void> downloadIfNotExist() {
        if (Files.exists(saveFilePath)) {
            return Future.succeededFuture();
        } else {
            return tryDownload();
        }
    }

    private InputStream makeInputStream() {
        try {
            return Files.newInputStream(saveFilePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cant create inputStream for: " + saveFilePath.getPath(), e);
        }
    }

    private Future<Void> tryDownload() {
        Future<Void> downloadFuture = Future.future();
        performBlockingDownload()
                .setHandler(event -> {
                    if (event.failed()) {
                        retryDownload(downloadFuture);
                    } else {
                        downloadFuture.complete();
                    }
                });
        return downloadFuture;
    }

    private void retryDownload(Future<Void> receivedFuture) {
        vertx.setTimer(retryInterval, retryTimerId -> {
            if (retryCount > 0) {
                retryCount--;
                performBlockingDownload().setHandler(h -> {
                    if (h.succeeded()) {
                        receivedFuture.complete();
                    } else {
                        retryDownload(receivedFuture);
                    }
                });
            } else {
                receivedFuture.fail(new RuntimeException());
            }
        });
    }

    private Future<Void> performBlockingDownload() {
        final FileOutputStream fileOutputStream;
        try {
            fileOutputStream = fileHelper.fromPath(saveFilePath);
        } catch (IOException e) {
            return Future.failedFuture(e);
        }

        final Future<Void> future = Future.future();
        vertx.executeBlocking(event -> download(fileOutputStream, event), false, future);
        return future;
    }

    private void download(FileOutputStream fileOutputStream, Future<Void> future) {
        long timeoutTimerId = 0;
        try (FileChannel channel = fileOutputStream.getChannel();
             ReadableByteChannel readableByteChannel = fileHelper.fromUrl(downloadUrl)) {

            // Remove possibly corrupted file after several retry
            Files.deleteIfExists(saveFilePath);

            timeoutTimerId = setTimeoutTimer(channel, readableByteChannel, fileOutputStream, future);
            channel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            cancelTimer(timeoutTimerId);

            future.complete();
        } catch (IOException e) {
            cancelTimer(timeoutTimerId);
            future.fail(new RuntimeException(String.format("Cant download file: %s, from url: %s", saveFilePath,
                    downloadUrl.getPath()), e));
        }
    }

    private long setTimeoutTimer(FileChannel channel, ReadableByteChannel readableByteChannel, FileOutputStream fileOutputStream, Future<Void> future) {
        return vertx.setTimer(timeout, event -> {
            close(fileOutputStream);
            close(channel);
            close(readableByteChannel);
            if (!future.succeeded()) {
                future.fail(new RuntimeException("Timeout"));
            }
        });
    }

    private void cancelTimer(long timeoutTimerId) {
        if (timeoutTimerId != 0) {
            vertx.cancelTimer(timeoutTimerId);
        }
    }

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}

