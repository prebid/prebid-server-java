package org.prebid.server.execution.file.syncer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.execution.retry.Retryable;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;

public abstract class FileSyncer {

    private static final Logger logger = LoggerFactory.getLogger(FileSyncer.class);

    private final FileProcessor fileProcessor;
    private final long updatePeriod;
    private final RetryPolicy retryPolicy;
    private final Vertx vertx;

    protected FileSyncer(FileProcessor fileProcessor,
                         long updatePeriod,
                         RetryPolicy retryPolicy,
                         Vertx vertx) {

        this.fileProcessor = Objects.requireNonNull(fileProcessor);
        this.updatePeriod = updatePeriod;
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.vertx = Objects.requireNonNull(vertx);
    }

    public void sync() {
        sync(retryPolicy);
    }

    private void sync(RetryPolicy currentRetryPolicy) {
        getFile()
                .compose(this::processFile)
                .onSuccess(ignored -> onSuccess())
                .onFailure(failure -> onFailure(currentRetryPolicy, failure));
    }

    protected abstract Future<String> getFile();

    private Future<?> processFile(String filePath) {
        return filePath != null
                ? fileProcessor.setDataPath(filePath)
                .onFailure(error -> logger.error("Can't process saved file: " + filePath))
                : Future.succeededFuture();
    }

    private void onSuccess() {
        doOnSuccess().onComplete(ignored -> setUpDeferredUpdate());
    }

    protected abstract Future<Void> doOnSuccess();

    private void setUpDeferredUpdate() {
        if (updatePeriod > 0) {
            vertx.setTimer(updatePeriod, ignored -> sync());
        }
    }

    private void onFailure(RetryPolicy currentRetryPolicy, Throwable failure) {
        doOnFailure(failure).onComplete(ignored -> retrySync(currentRetryPolicy));
    }

    protected abstract Future<Void> doOnFailure(Throwable throwable);

    private void retrySync(RetryPolicy currentRetryPolicy) {
        if (currentRetryPolicy instanceof Retryable policy) {
            logger.info(
                    "Retrying file sync for {} with policy: {}",
                    fileProcessor.getClass().getSimpleName(),
                    policy);
            vertx.setTimer(policy.delay(), timerId -> sync(policy.next()));
        } else {
            setUpDeferredUpdate();
        }
    }
}
