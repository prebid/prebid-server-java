package org.prebid.server.execution.file.syncer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.execution.file.supplier.LocalFileSupplier;
import org.prebid.server.execution.retry.RetryPolicy;

public class LocalFileSyncer extends FileSyncer {

    private final LocalFileSupplier localFileSupplier;

    protected LocalFileSyncer(FileProcessor fileProcessor,
                              String localFile,
                              long updatePeriod,
                              RetryPolicy retryPolicy,
                              Vertx vertx) {

        super(fileProcessor, updatePeriod, retryPolicy, vertx);

        localFileSupplier = new LocalFileSupplier(localFile, vertx.fileSystem());
    }

    @Override
    protected Future<String> getFile() {
        return localFileSupplier.get();
    }

    @Override
    protected Future<Void> doOnSuccess() {
        return Future.succeededFuture();
    }

    @Override
    protected Future<Void> doOnFailure(Throwable throwable) {
        return Future.succeededFuture();
    }
}
