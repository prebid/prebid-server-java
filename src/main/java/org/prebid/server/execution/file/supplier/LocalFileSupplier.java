package org.prebid.server.execution.file.supplier;

import io.vertx.core.Future;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class LocalFileSupplier implements Supplier<Future<String>> {

    private final String filePath;
    private final FileSystem fileSystem;
    private final AtomicLong lastSupplyTime;

    public LocalFileSupplier(String filePath, FileSystem fileSystem) {
        this.filePath = Objects.requireNonNull(filePath);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        lastSupplyTime = new AtomicLong(Long.MIN_VALUE);
    }

    @Override
    public Future<String> get() {
        return fileSystem.exists(filePath)
                .compose(exists -> exists
                        ? fileSystem.props(filePath)
                        : Future.failedFuture("File %s not found.".formatted(filePath)))
                .map(this::getFileIfModified);
    }

    private String getFileIfModified(FileProps fileProps) {
        final long lastModifiedTime = lasModifiedTime(fileProps);
        final long lastSupplyTime = this.lastSupplyTime.get();

        if (lastSupplyTime < lastModifiedTime) {
            this.lastSupplyTime.compareAndSet(lastSupplyTime, lastModifiedTime);
            return filePath;
        }

        return null;
    }

    private static long lasModifiedTime(FileProps fileProps) {
        return Math.max(fileProps.creationTime(), fileProps.lastModifiedTime());
    }
}
