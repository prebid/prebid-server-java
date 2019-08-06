package org.prebid.server.execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class RemoteFileSyncerTest extends VertxTest {

    private static final long TIMEOUT = 10000;
    private static final int RETRY_COUNT = 2;
    private static final long RETRY_INTERVAL = 2000;
    private static final String EXAMPLE_URL = "https://example.com";
    private static final String DOMAIN = "example.com";
    private static final String FILE_PATH = "./src/test/resources/org/prebid/server/geolocation/test.pdf";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;

    @Mock
    private FileSystem fileSystem;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Consumer<String> stringResultConsumer;

    @Mock
    private AsyncFile asyncFile;

    @Mock
    private HttpClientRequest httpClientRequest;

    @Mock
    private HttpClientResponse httpClientResponse;

    @Captor
    private ArgumentCaptor<Handler<AsyncResult<Boolean>>> isExistCaptor;

    @Captor
    private ArgumentCaptor<Handler<AsyncResult<AsyncFile>>> openFileCaptor;

    @Captor
    private ArgumentCaptor<Handler<HttpClientResponse>> responseCaptor;

    @Captor
    private ArgumentCaptor<Handler<Void>> responseEndCaptor;

    @Captor
    private ArgumentCaptor<Handler<Long>> retryCaptor;

    @Captor
    private ArgumentCaptor<Handler<AsyncResult<Void>>> futureCaptor;

    private RemoteFileSyncer remoteFileSyncer;

    @Before
    public void setUp() {
        when(vertx.fileSystem()).thenReturn(fileSystem);
        remoteFileSyncer = new RemoteFileSyncer(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx);
    }

    @Test
    public void syncForFilepathShouldTriggerConsumerAcceptWithoutDownloadingWhenFileIsExist() {
        // given
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        verify(vertx).fileSystem();
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(true));

        // when and then
        verify(stringResultConsumer).accept(FILE_PATH);
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void syncForFilepathShouldNotTriggerConsumerAcceptWhenCantCheckIfFileExist() {
        // given
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        verify(vertx).fileSystem();
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));

        // when and then
        verifyZeroInteractions(stringResultConsumer);
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void syncForFilepathShouldTriggerConsumerAcceptAfterDownload() {
        // given
        when(asyncFile.flush()).thenReturn(asyncFile);
        when(httpClient.get(any(), any(), any())).thenReturn(httpClientRequest);

        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);

        // when
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        // then
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(false));

        verify(fileSystem).open(eq(FILE_PATH), any(), openFileCaptor.capture());
        openFileCaptor.getValue().handle(Future.succeededFuture(asyncFile));

        verify(httpClient).get(eq(DOMAIN), eq(EXAMPLE_URL), responseCaptor.capture());
        responseCaptor.getValue().handle(httpClientResponse);

        // Timeout timer
        verify(vertx).setTimer(eq(TIMEOUT), any());

        // Response ended
        verify(httpClientResponse).endHandler(responseEndCaptor.capture());
        responseEndCaptor.getValue().handle(null);

        verify(vertx).cancelTimer(timerId);
        verify(asyncFile).close(futureCaptor.capture());
        futureCaptor.getValue().handle(Future.succeededFuture());

        verify(stringResultConsumer).accept(FILE_PATH);
    }

    @Test
    public void syncForFilepathShouldRetryAfterFailedDownload() {
        // when
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        // then
        // First download fail
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(false));

        verify(fileSystem).open(eq(FILE_PATH), any(), openFileCaptor.capture());
        openFileCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));

        // Retries
        for (int i = 1; i <= RETRY_COUNT; i++) {
            verify(vertx, times(i)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
            retryCaptor.getValue().handle(0L);

            // Check if we need to delete file
            verify(fileSystem, times(i + 1)).exists(eq(FILE_PATH), isExistCaptor.capture());
            isExistCaptor.getValue().handle(Future.succeededFuture(false));

            verify(fileSystem, times(i + 1)).open(eq(FILE_PATH), any(), openFileCaptor.capture());
            openFileCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));
        }

        // Final call when RETRY_COUNT = 0
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
        retryCaptor.getValue().handle(0L);

        verifyZeroInteractions(stringResultConsumer);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldRetryAfterFailedDownloadWhenDeleteFileIsFailed() {
        // when
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        // then
        // First download fail
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(false));

        verify(fileSystem).open(eq(FILE_PATH), any(), openFileCaptor.capture());
        openFileCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));

        // Retries
        for (int i = 1; i <= RETRY_COUNT; i++) {
            verify(vertx, times(i)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
            retryCaptor.getValue().handle(0L);

            // Check if we need to delete file
            verify(fileSystem, times(i + 1)).exists(eq(FILE_PATH), isExistCaptor.capture());
            isExistCaptor.getValue().handle(Future.succeededFuture(true));

            verify(fileSystem, times(i)).delete(eq(FILE_PATH), futureCaptor.capture());
            futureCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));
        }

        // Final call when RETRY_COUNT = 0
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
        retryCaptor.getValue().handle(0L);

        verifyZeroInteractions(stringResultConsumer);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldRetryAfterFailedDownloadAndTriggerConsumerAcceptAfterSuccessfullyDownload() {
        // given
        when(asyncFile.flush()).thenReturn(asyncFile);
        when(httpClient.get(any(), any(), any())).thenReturn(httpClientRequest);

        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);

        // when
        remoteFileSyncer.syncForFilepath(stringResultConsumer);

        // then
        // First download fail
        verify(fileSystem).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(false));

        verify(fileSystem).open(eq(FILE_PATH), any(), openFileCaptor.capture());
        openFileCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));

        // Retries
        for (int i = 1; i < RETRY_COUNT; i++) {
            verify(vertx, times(i)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
            retryCaptor.getValue().handle(0L);

            // Check if we need to delete file
            verify(fileSystem, times(i + 1)).exists(eq(FILE_PATH), isExistCaptor.capture());
            isExistCaptor.getValue().handle(Future.succeededFuture(true));

            verify(fileSystem, times(i)).delete(eq(FILE_PATH), futureCaptor.capture());
            futureCaptor.getValue().handle(Future.failedFuture(new RuntimeException()));
        }

        // Final retry
        verify(vertx, times(RETRY_COUNT)).setTimer(eq(RETRY_INTERVAL), retryCaptor.capture());
        retryCaptor.getValue().handle(0L);

        verify(fileSystem, times(RETRY_COUNT + 1)).exists(eq(FILE_PATH), isExistCaptor.capture());
        isExistCaptor.getValue().handle(Future.succeededFuture(false));

        // Successful download
        verify(fileSystem, times(2)).open(eq(FILE_PATH), any(), openFileCaptor.capture());
        openFileCaptor.getValue().handle(Future.succeededFuture(asyncFile));

        verify(httpClient).get(eq(DOMAIN), eq(EXAMPLE_URL), responseCaptor.capture());
        responseCaptor.getValue().handle(httpClientResponse);

        // Timeout timer
        verify(vertx).setTimer(eq(TIMEOUT), any());

        // Response ended
        verify(httpClientResponse).endHandler(responseEndCaptor.capture());
        responseEndCaptor.getValue().handle(null);

        verify(vertx).cancelTimer(timerId);
        verify(asyncFile).close(futureCaptor.capture());
        futureCaptor.getValue().handle(Future.succeededFuture());

        verify(stringResultConsumer).accept(FILE_PATH);
    }
}

