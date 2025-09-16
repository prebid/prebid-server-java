package org.prebid.server.execution.file.syncer;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.execution.file.FileProcessor;
import org.prebid.server.execution.retry.FixedIntervalRetryPolicy;
import org.prebid.server.execution.retry.NonRetryable;
import org.prebid.server.execution.retry.RetryPolicy;
import org.testcontainers.shaded.org.apache.commons.lang3.NotImplementedException;

import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FileSyncerTest {

    private static final String SAVE_PATH = "/path/to/file";

    @Mock
    private FileProcessor fileProcessor;

    @Mock
    private Vertx vertx;

    @BeforeEach
    public void setUp() {
        given(vertx.executeBlocking(Mockito.<Callable<?>>any())).willAnswer(invocation -> {
            try {
                return Future.succeededFuture(((Callable<?>) invocation.getArgument(0)).call());
            } catch (Throwable e) {
                return Future.failedFuture(e);
            }
        });
    }

    @Test
    public void syncShouldCallExpectedMethodsOnSuccessWhenNoReturnedFile() {
        // given
        final FileSyncer fileSyncer = fileSyncer(NonRetryable.instance());
        given(fileSyncer.getFile()).willReturn(Future.succeededFuture());

        // when
        fileSyncer.sync();

        // then
        verifyNoInteractions(fileProcessor);
        verify(fileSyncer).doOnSuccess();
        verify(vertx).setTimer(eq(1000L), any());
    }

    @Test
    public void syncShouldCallExpectedMethodsOnSuccess() {
        // given
        final FileSyncer fileSyncer = fileSyncer(NonRetryable.instance());
        given(fileSyncer.getFile()).willReturn(Future.succeededFuture(SAVE_PATH));
        given(fileProcessor.setDataPath(eq(SAVE_PATH))).willReturn(Future.succeededFuture());

        // when
        fileSyncer.sync();

        // then
        verify(fileProcessor).setDataPath(eq(SAVE_PATH));
        verify(fileSyncer).doOnSuccess();
        verify(vertx).setTimer(eq(1000L), any());
    }

    @Test
    public void syncShouldCallExpectedMethodsOnFailure() {
        // given
        final FileSyncer fileSyncer = fileSyncer(NonRetryable.instance());
        given(fileSyncer.getFile()).willReturn(Future.succeededFuture(SAVE_PATH));
        given(fileProcessor.setDataPath(eq(SAVE_PATH))).willReturn(Future.failedFuture("Failure"));

        // when
        fileSyncer.sync();

        // then
        verify(fileProcessor).setDataPath(eq(SAVE_PATH));
        verify(fileSyncer).doOnFailure(any());
        verify(vertx).setTimer(eq(1000L), any());
    }

    @Test
    public void syncShouldCallExpectedMethodsOnFailureWithRetryable() {
        // given
        final FileSyncer fileSyncer = fileSyncer(FixedIntervalRetryPolicy.limited(10L, 1));
        given(fileSyncer.getFile()).willReturn(Future.succeededFuture(SAVE_PATH));
        given(fileProcessor.setDataPath(eq(SAVE_PATH))).willReturn(Future.failedFuture("Failure"));

        final Promise<Void> promise = Promise.promise();
        given(vertx.setTimer(eq(10L), any())).willAnswer(invocation -> {
            promise.future().onComplete(ignore -> ((Handler<Long>) invocation.getArgument(1)).handle(1L));
            return 1L;
        });

        // when
        fileSyncer.sync();

        // then
        verify(fileProcessor).setDataPath(eq(SAVE_PATH));
        verify(fileSyncer).doOnFailure(any());
        verify(vertx).setTimer(eq(10L), any());

        // when
        promise.complete();

        // then
        verify(fileProcessor, times(2)).setDataPath(eq(SAVE_PATH));
        verify(fileSyncer, times(2)).doOnFailure(any());
        verify(vertx).setTimer(eq(1000L), any());
    }

    private FileSyncer fileSyncer(RetryPolicy retryPolicy) {
        return spy(new TestFileSyncer(fileProcessor, 1000L, retryPolicy, vertx));
    }

    private static class TestFileSyncer extends FileSyncer {

        protected TestFileSyncer(FileProcessor fileProcessor,
                                 long updatePeriod,
                                 RetryPolicy retryPolicy,
                                 Vertx vertx) {

            super(fileProcessor, updatePeriod, retryPolicy, vertx);
        }

        @Override
        public Future<String> getFile() {
            return Future.failedFuture(new NotImplementedException());
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
}
