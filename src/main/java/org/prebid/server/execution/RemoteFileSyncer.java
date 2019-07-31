package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Works with remote web resource.
 */
public class RemoteFileSyncer {

    private final URL downloadUrl;  // url to resource to be downloaded

    private final File saveFilePath; // full path on file system where downloaded file located
    private final long retryInterval; // how long to wait between failed retries
    private long timerId;
    private int retryCount; // how many times try to download

    public RemoteFileSyncer(URL downloadUrl, File saveFilePath, int retryCount, long retryInterval) {
        this.downloadUrl = downloadUrl;
        this.saveFilePath = saveFilePath;

        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
    }

    /**
     * Fetches remote file and executes given callback with {@link InputStream} on finish.
     */
    public void syncForInputStream(Consumer<InputStream> consumer) {
        downloadIfNotExist().setHandler(event -> consumer.accept(makeInputStream()));
    }

    private Future<Void> downloadIfNotExist() {
        if (saveFilePath.exists()) {
            return Future.succeededFuture();
        } else {
            final Future<Void> future = Future.future();
            sync(future);
            return future;
        }
    }

    private InputStream makeInputStream() {
        try {
            return Files.newInputStream(saveFilePath.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cant create inputStream for: " + saveFilePath.getPath(), e);
        }
    }

    /**
     * Fetches remote file and executes given callback with filepath on finish.
     */
    public void syncForFilepath(Consumer<String> consumer) {
        downloadIfNotExist().setHandler(event -> consumer.accept(saveFilePath.getPath()));
    }

    /**
     * Downloads remote file.
     */
    private void sync(Future<Void> recieveFuture) {
        final FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(saveFilePath);
        } catch (IOException e) {
            Future.failedFuture(e);
            return;
        }


//        final long l = Vertx.vertx().setTimer(downloadTimer, event -> {
//            fileOutputStream.close();
//.....});)


        download(fileOutputStream)
                .setHandler(event -> {
                    if (event.failed()) {
                        if (retryCount > 0) {
                            retryCount--;

//                            if (timerId > 0) {
//                                Vertx.vertx().cancelTimer(timerId);
//                            }

                            Vertx.vertx().setTimer(retryInterval, downloadIdTimer -> downloadWrapper(fileOutputStream, recieveFuture, downloadIdTimer));
                        } else {
                            recieveFuture.fail(new RuntimeException());
                        }
                    } else {
                        recieveFuture.complete();
                    }
                });
    }

    private void downloadWrapper(FileOutputStream fileOutputStream, Future recieveFuture, long downloadIdTimer) {
        this.timerId = timerId;
        download(fileOutputStream).handle(recieveFuture);
    }

    private Future<Void> download(FileOutputStream fileOutputStream) {
        final Future<Void> future = Future.future();

        Vertx.vertx().executeBlocking(event -> {

            try (FileChannel channel = fileOutputStream.getChannel();
                 ReadableByteChannel readableByteChannel = Channels.newChannel(downloadUrl.openStream())) {

                channel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

                event.complete();
            } catch (IOException e) {
                event.fail(new RuntimeException(String.format("Cant download file: %s, from url: %s", saveFilePath,
                        downloadUrl.getPath()), e));
            }
        }, false, future);

        return future;

    }
}

