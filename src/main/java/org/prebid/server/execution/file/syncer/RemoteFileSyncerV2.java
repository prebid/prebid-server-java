package org.prebid.server.execution.file.syncer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.execution.file.supplier.LocalFileSupplier;
import org.prebid.server.execution.file.supplier.RemoteFileSupplier;
import org.prebid.server.execution.retry.RetryPolicy;

public class RemoteFileSyncerV2 extends FileSyncer {

    private final LocalFileSupplier localFileSupplier;
    private final RemoteFileSupplier remoteFileSupplier;

    protected RemoteFileSyncerV2(FileProcessor fileProcessor,
                                 String downloadUrl,
                                 String saveFilePath,
                                 String tmpFilePath,
                                 HttpClient httpClient,
                                 long timeout,
                                 boolean checkSize,
                                 long updatePeriod,
                                 RetryPolicy retryPolicy,
                                 Vertx vertx) {

        super(fileProcessor, updatePeriod, retryPolicy, vertx);

        final FileSystem fileSystem = vertx.fileSystem();
        localFileSupplier = new LocalFileSupplier(saveFilePath, fileSystem);
        remoteFileSupplier = new RemoteFileSupplier(
                downloadUrl,
                saveFilePath,
                tmpFilePath,
                httpClient,
                timeout,
                checkSize,
                fileSystem);
    }

    @Override
    protected Future<String> getFile() {
        return localFileSupplier.get()
                .otherwiseEmpty()
                .compose(localFile -> localFile != null
                        ? Future.succeededFuture(localFile)
                        : remoteFileSupplier.get());
    }

    @Override
    protected Future<Void> doOnSuccess() {
        remoteFileSupplier.deleteBackup();
        forceLastSupplyTimeUpdate();
        return Future.succeededFuture();
    }

    @Override
    protected Future<Void> doOnFailure(Throwable throwable) {
        return remoteFileSupplier.restoreFromBackup()
                .onSuccess(ignore -> forceLastSupplyTimeUpdate());
    }

    private void forceLastSupplyTimeUpdate() {
        localFileSupplier.get();
    }
}
