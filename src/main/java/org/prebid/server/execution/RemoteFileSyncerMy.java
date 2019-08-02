package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncerMy {

    private final URL downloadUrl;  // url to resource to be downloaded

    private final File saveFilePath; // full path on file system where downloaded file located
    private final long retryInterval; // how long to wait between failed retries
    private final long refreshPeriod; // period for file updates (0 for one time downloading)
    private final int timeout;
    private Vertx vertx;
    private int retryCount; // how many times try to download

    public RemoteFileSyncerMy(URL downloadUrl, File saveFilePath, int retryCount, long retryInterval,
                              long refreshPeriod, int timeout, Vertx vertx) {
        this.downloadUrl = Objects.requireNonNull(downloadUrl);
        this.saveFilePath = Objects.requireNonNull(saveFilePath);

        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.vertx = vertx;
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
        downloadIfNotExist().setHandler(event -> consumer.accept(saveFilePath.getPath()));
    }

    private Future<Void> downloadIfNotExist() {
        if (saveFilePath.exists()) {
            return Future.succeededFuture();
        } else {
            return tryDownload();
        }
    }

    private InputStream makeInputStream() {
        try {
            return Files.newInputStream(saveFilePath.toPath());
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
            fileOutputStream = new FileOutputStream(saveFilePath);
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
             ReadableByteChannel readableByteChannel = Channels.newChannel(downloadUrl.openStream())) {

            // Remove possibly corrupted file
            Files.delete(saveFilePath.toPath());

            //Timeout in executeBlocking ?
            timeoutTimerId = setTimeoutTimer(channel, readableByteChannel, fileOutputStream, future);

            channel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            //I am not sure. Does future can switch it`s state between our operations? (after timer is reached)
            future.complete();
        } catch (IOException e) {
            cancelTimer(timeoutTimerId);
            future.fail(new RuntimeException(String.format("Cant download file: %s, from url: %s", saveFilePath,
                    downloadUrl.getPath()), e));
        }
    }

    private void cancelTimer(long timeoutTimerId) {
        if (timeoutTimerId != 0) {
            vertx.cancelTimer(timeoutTimerId);
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

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}

