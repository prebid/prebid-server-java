package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class RemoteFileSyncerTest extends VertxTest {

    private static final long TIMEOUT = 10000;
    private static final int RETRY_COUNT = 2;
    private static final long RETRY_INTERVAL = 2000;
    private static final String EXAMPLE_URL = "https://example.com";
    private static final String FILE_PATH = String.join(File.separator, "fake", "path", "to", "file.pdf");
    private static final String DIR_PATH = String.join(File.separator, "fake", "path", "to");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private FileSystem fileSystem;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Consumer<String> filePathConsumer;
    @Mock
    private AsyncFile asyncFile;
    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private HttpClientResponse httpClientResponse;

    private RemoteFileSyncer remoteFileSyncer;

    @Before
    public void setUp() {
        when(vertx.fileSystem()).thenReturn(fileSystem);
        remoteFileSyncer = RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx);
    }

    @Test
    public void shouldThrowNullPointerExceptionWhenIllegalArgumentsWhenNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(EXAMPLE_URL, null, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx));
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, null, vertx));
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, null));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenIllegalArguments() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> RemoteFileSyncer.create(null, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RemoteFileSyncer.create("bad url", FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx));
    }

    @Test
    public void creteShouldCreateDirWithWritePermissionIfDirNotExist() {
        // given
        reset(fileSystem);
        when(fileSystem.existsBlocking(eq(DIR_PATH))).thenReturn(false);

        // when
        RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx);

        // then
        verify(fileSystem).mkdirsBlocking(eq(DIR_PATH));
    }

    @Test
    public void createShouldCreateDirWithWritePermissionIfItsNotDir() {
        // given
        final FileProps fileProps = mock(FileProps.class);
        reset(fileSystem);
        when(fileSystem.existsBlocking(eq(DIR_PATH))).thenReturn(true);
        when(fileSystem.propsBlocking(eq(DIR_PATH))).thenReturn(fileProps);
        when(fileProps.isDirectory()).thenReturn(false);

        // when
        RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx);

        // then
        verify(fileSystem).mkdirsBlocking(eq(DIR_PATH));
    }

    @Test
    public void createShouldThrowPreBidExceptionWhenPropsThrowException() {
        // given
        reset(fileSystem);
        when(fileSystem.existsBlocking(eq(DIR_PATH))).thenReturn(true);
        when(fileSystem.propsBlocking(eq(DIR_PATH))).thenThrow(FileSystemException.class);

        // when and then
        assertThatThrownBy(() -> RemoteFileSyncer.create(EXAMPLE_URL, FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT, httpClient, vertx))
                .isInstanceOf(PreBidException.class);
    }

    @Test
    public void syncForFilepathShouldTriggerConsumerAcceptWithoutDownloadingWhenFileIsExist() {
        // given
        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)));

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx).fileSystem();
        verify(fileSystem).exists(eq(FILE_PATH), any());
        verify(filePathConsumer).accept(FILE_PATH);
        verifyZeroInteractions(httpClient);
        verifyZeroInteractions(vertx);
    }

    @Test
    public void syncForFilepathShouldNotTriggerConsumerAcceptWhenCantCheckIfFileExist() {
        // given
        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())));

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx).fileSystem();
        verify(fileSystem).exists(eq(FILE_PATH), any());
        verifyZeroInteractions(filePathConsumer);
        verifyNoMoreInteractions(vertx);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldTriggerConsumerAcceptAfterDownload() {
        // given
        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);
        when(asyncFile.flush()).thenReturn(asyncFile);

        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)));

        given(fileSystem.open(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(asyncFile), 2));

        given(httpClient.getAbs(any(), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(httpClientResponse, httpClientRequest, 1));

        given(httpClientResponse.endHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(null, 0));

        doAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(), 0))
                .when(asyncFile).close(any());

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(fileSystem).exists(eq(FILE_PATH), any());
        verify(fileSystem).open(eq(FILE_PATH), any(), any());

        // Response handled
        verify(httpClient).getAbs(eq(EXAMPLE_URL), any());
        verify(vertx).setTimer(eq(TIMEOUT), any());
        verify(httpClientResponse).endHandler(any());
        verify(vertx).cancelTimer(timerId);
        verify(asyncFile).close(any());

        verify(filePathConsumer).accept(FILE_PATH);
    }

    @Test
    public void syncForFilepathShouldRetryAfterFailedDownload() {
        // given
        given(fileSystem.exists(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)));

        given(fileSystem.open(any(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException()), 2));

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 2)).exists(eq(FILE_PATH), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(FILE_PATH), any(), any());

        verifyZeroInteractions(httpClient);
        verifyZeroInteractions(filePathConsumer);
    }

    @Test
    public void syncForFilepathShouldRetryWhenDeleteFileIsFailed() {
        // then
        given(fileSystem.exists(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)));

        given(fileSystem.open(any(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException()), 2));

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        given(fileSystem.delete(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())));

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 2)).exists(eq(FILE_PATH), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).delete(eq(FILE_PATH), any());

        verifyZeroInteractions(httpClient);
        verifyZeroInteractions(filePathConsumer);
    }


    @Test
    public void syncForFilepathShouldRetryWhenTimeoutIsReached() {
        // given
        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)));

        given(fileSystem.open(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(asyncFile), 2));

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        given(fileSystem.delete(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        given(httpClient.getAbs(any(), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(httpClientResponse, httpClientRequest, 1));
        given(vertx.setTimer(eq(TIMEOUT), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(null, 22L, 1));

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 2)).exists(eq(FILE_PATH), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(FILE_PATH), any(), any());

        // Response handled
        verify(httpClient, times(RETRY_COUNT + 1)).getAbs(eq(EXAMPLE_URL), any());
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(TIMEOUT), any());
        verify(asyncFile, times(RETRY_COUNT + 1)).close();

        verifyZeroInteractions(filePathConsumer);
    }

    @Test
    public void syncForFilepathShouldRetryAndTriggerConsumerAcceptAfterDownload() {
        // given
        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);
        when(asyncFile.flush()).thenReturn(asyncFile);

        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)));

        given(fileSystem.open(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException()), 2))
                // After file deleted successfully
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(asyncFile), 2));

        // setTimer also used for timeout setup which we don`t want to trigger
        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        given(fileSystem.delete(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        given(httpClient.getAbs(any(), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(httpClientResponse, httpClientRequest, 1));
        given(httpClientResponse.endHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(null, 0));
        doAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(), 0))
                .when(asyncFile).close(any());

        // when
        remoteFileSyncer.syncForFilepath(filePathConsumer);

        // then
        verify(vertx, times(2)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(3)).exists(eq(FILE_PATH), any());
        verify(fileSystem, times(2)).open(eq(FILE_PATH), any(), any());

        // Response handled
        verify(httpClient).getAbs(eq(EXAMPLE_URL), any());
        verify(vertx).setTimer(eq(TIMEOUT), any());
        verify(httpClientResponse).endHandler(any());
        verify(vertx).cancelTimer(timerId);
        verify(asyncFile).close(any());

        verify(filePathConsumer).accept(FILE_PATH);
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj, int index) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(index)).handle(obj);
            return inv.getMock();
        };
    }

    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return withSelfAndPassObjectToHandler(obj, 1);
    }

    @SuppressWarnings("unchecked")
    private static <T, V> Answer<Object> withReturnObjectAndPassObjectToHandler(T obj, V ret, int index) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(index)).handle(obj);
            return ret;
        };
    }
}

