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
import io.vertx.core.http.HttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private static final long UPDATE_INTERVAL = 2000000;
    private static final String SOURCE_URL = "https://example.com";
    private static final String FILE_PATH = String.join(File.separator, "fake", "path", "to", "file.pdf");
    private static final String TMP_FILE_PATH = String.join(File.separator, "tmp", "fake", "path", "to", "file.pdf");
    private static final String DIR_PATH = String.join(File.separator, "fake", "path", "to");
    private static final Long FILE_SIZE = 2131242L;
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private Vertx vertx;

    @Mock
    private FileSystem fileSystem;

    @Mock
    private HttpClient httpClient;

    @Mock
    private RemoteFileProcessor remoteFileProcessor;
    @Mock
    private AsyncFile asyncFile;

    @Mock
    private FileProps fileProps;

    @Mock
    private HttpClientRequest httpClientRequest;

    @Mock
    private HttpClientResponse httpClientResponse;

    private RemoteFileSyncer remoteFileSyncer;

    @Before
    public void setUp() {
        when(vertx.fileSystem()).thenReturn(fileSystem);
        remoteFileSyncer = RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, 0, httpClient, vertx, fileSystem);
    }

    @Test
    public void shouldThrowNullPointerExceptionWhenIllegalArgumentsWhenNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(SOURCE_URL, null, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT,
                        UPDATE_INTERVAL, httpClient, vertx, fileSystem));
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                        TIMEOUT, UPDATE_INTERVAL, null, vertx, fileSystem));
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, null, fileSystem));
        assertThatNullPointerException().isThrownBy(
                () -> RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, null));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenIllegalArguments() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> RemoteFileSyncer.create(null, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem));
        assertThatIllegalArgumentException().isThrownBy(
                () -> RemoteFileSyncer.create("bad url", FILE_PATH, TMP_FILE_PATH, RETRY_COUNT,
                        RETRY_INTERVAL, TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem));
    }

    @Test
    public void creteShouldCreateDirWithWritePermissionIfDirNotExist() {
        // given
        reset(fileSystem);
        when(fileSystem.existsBlocking(eq(DIR_PATH))).thenReturn(false);

        // when
        RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT,
                UPDATE_INTERVAL, httpClient, vertx, fileSystem);

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
        RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL, TIMEOUT,
                UPDATE_INTERVAL, httpClient, vertx, fileSystem);

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
        assertThatThrownBy(() -> RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT,
                RETRY_INTERVAL, TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem))
                .isInstanceOf(PreBidException.class);
    }

    @Test
    public void syncForFilepathShouldNotTriggerServiceWhenCantCheckIfUsableFileExist() {
        // given
        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())));

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem).exists(eq(FILE_PATH), any());
        verifyZeroInteractions(remoteFileProcessor);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldRetryWhenRemoteFileProcessorIsFailed() {
        // given
        given(remoteFileProcessor.setDataPath(FILE_PATH))
                .willReturn(Future.failedFuture("Bad db file"));

        given(fileSystem.exists(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)))
                // Mock removal of file
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(false)));

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH), any());
        verify(remoteFileProcessor, times(1)).setDataPath(FILE_PATH);
        verify(fileSystem).open(eq(TMP_FILE_PATH), any(), any());
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateWhenHeadRequestReturnInvalidHead() {
        // given
        remoteFileSyncer = RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem);

        givenTriggerUpdate();

        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn("notnumber");

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH), any());
        verify(httpClient).headAbs(eq(SOURCE_URL), any());
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verify(vertx, times(2)).setTimer(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateWhenPropsIsFailed() {
        // given
        remoteFileSyncer = RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem);

        givenTriggerUpdate();

        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new IllegalArgumentException("ERROR"))));

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH), any());
        verify(httpClient).headAbs(eq(SOURCE_URL), any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH), any());
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verify(vertx, times(2)).setTimer(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateServiceWhenSizeEqualsContentLength() {
        // given
        remoteFileSyncer = RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem);

        givenTriggerUpdate();

        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(fileProps)));

        doReturn(FILE_SIZE).when(fileProps).size();

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH), any());
        verify(httpClient).headAbs(eq(SOURCE_URL), any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH), any());
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verify(vertx, times(2)).setTimer(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldUpdateServiceWhenSizeNotEqualsContentLength() {
        // given
        remoteFileSyncer = RemoteFileSyncer.create(SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_COUNT, RETRY_INTERVAL,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx, fileSystem);

        givenTriggerUpdate();

        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(fileProps)));

        given(fileSystem.delete(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(fileProps)));

        doReturn(123L).when(fileProps).size();

        // Download
        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);
        when(asyncFile.flush()).thenReturn(asyncFile);

        given(fileSystem.open(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(asyncFile), 2));

        given(httpClient.getAbs(any(), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(httpClientResponse, httpClientRequest, 1));

        given(httpClientResponse.endHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(null, 0));

        doAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(), 0))
                .when(asyncFile).close(any());

        given(fileSystem.move(anyString(), any(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(), 3));

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(httpClient).headAbs(eq(SOURCE_URL), any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH), any());

        // Download
        verify(fileSystem).open(eq(TMP_FILE_PATH), any(), any());
        verify(vertx).setTimer(eq(TIMEOUT), any());
        verify(httpClient).getAbs(eq(SOURCE_URL), any());
        verify(httpClientResponse).endHandler(any());
        verify(asyncFile).close(any());
        verify(vertx).cancelTimer(timerId);

        verify(remoteFileProcessor, times(2)).setDataPath(any());
        verify(vertx, times(2)).setTimer(eq(UPDATE_INTERVAL), any());
        verify(fileSystem).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verifyNoMoreInteractions(httpClient);
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
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(TMP_FILE_PATH), any(), any());

        verifyZeroInteractions(httpClient);
        verifyZeroInteractions(remoteFileProcessor);
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
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())));

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 2)).delete(eq(TMP_FILE_PATH), any());

        verifyZeroInteractions(httpClient);
        verifyZeroInteractions(remoteFileProcessor);
    }

    @Test
    public void syncForFilepathShouldDownloadFilesAndNotUpdateWhenUpdatePeriodIsNotSet() {
        // given
        final long timerId = 22L;
        when(vertx.setTimer(eq(TIMEOUT), any())).thenReturn(timerId);
        when(asyncFile.flush()).thenReturn(asyncFile);

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

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

        given(fileSystem.move(anyString(), any(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(), 3));

        // when
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(fileSystem).open(eq(TMP_FILE_PATH), any(), any());
        verify(vertx).setTimer(eq(TIMEOUT), any());
        verify(httpClient).getAbs(eq(SOURCE_URL), any());
        verify(httpClientResponse).endHandler(any());
        verify(asyncFile).close(any());
        verify(vertx).cancelTimer(timerId);
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verify(vertx, never()).setTimer(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
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
        remoteFileSyncer.syncForFilepath(remoteFileProcessor);

        // then
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(TMP_FILE_PATH), any(), any());

        // Response handled
        verify(httpClient, times(RETRY_COUNT + 1)).getAbs(eq(SOURCE_URL), any());
        verify(vertx, times(RETRY_COUNT + 1)).setTimer(eq(TIMEOUT), any());
        verify(asyncFile, times(RETRY_COUNT + 1)).close();

        verifyZeroInteractions(remoteFileProcessor);
    }

    private void givenTriggerUpdate() {
        given(fileSystem.exists(anyString(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture(true)));

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        given(vertx.setTimer(eq(UPDATE_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(123L, 123L, 1))
                .willReturn(123L);

        given(httpClient.headAbs(anyString(), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(httpClientResponse, httpClientRequest, 1));

        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);
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

