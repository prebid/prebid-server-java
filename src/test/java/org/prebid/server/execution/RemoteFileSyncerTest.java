package org.prebid.server.execution;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
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
import org.prebid.server.execution.retry.FixedIntervalRetryPolicy;
import org.prebid.server.execution.retry.RetryPolicy;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RemoteFileSyncerTest extends VertxTest {

    private static final long TIMEOUT = 10000;
    private static final int RETRY_COUNT = 2;
    private static final long RETRY_INTERVAL = 2000;
    private static final RetryPolicy RETRY_POLICY = FixedIntervalRetryPolicy.limited(RETRY_INTERVAL, RETRY_COUNT);
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
        remoteFileSyncer = new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                TIMEOUT, 0, httpClient, vertx);
    }

    @Test
    public void shouldThrowNullPointerExceptionWhenIllegalArgumentsWhenNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, null, TMP_FILE_PATH, RETRY_POLICY, TIMEOUT,
                        UPDATE_INTERVAL, httpClient, vertx));
        assertThatNullPointerException().isThrownBy(
                () -> new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                        TIMEOUT, UPDATE_INTERVAL, null, vertx));
        assertThatNullPointerException().isThrownBy(
                () -> new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, null));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenIllegalArguments() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RemoteFileSyncer(remoteFileProcessor, null, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, vertx));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RemoteFileSyncer(remoteFileProcessor, "bad url", FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                        TIMEOUT, UPDATE_INTERVAL, httpClient, vertx));
    }

    @Test
    public void creteShouldCreateDirWithWritePermissionIfDirNotExist() {
        // given
        reset(fileSystem);
        when(fileSystem.existsBlocking(eq(DIR_PATH))).thenReturn(false);

        // when
        new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY, TIMEOUT,
                UPDATE_INTERVAL, httpClient, vertx);

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
        new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY, TIMEOUT,
                UPDATE_INTERVAL, httpClient, vertx);

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
        assertThatThrownBy(() -> new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH,
                RETRY_POLICY, TIMEOUT, UPDATE_INTERVAL, httpClient, vertx))
                .isInstanceOf(PreBidException.class);
    }

    @Test
    public void syncForFilepathShouldNotTriggerServiceWhenCantCheckIfUsableFileExist() {
        // given
        given(fileSystem.exists(anyString()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        // when
        remoteFileSyncer.sync();

        // then
        verify(fileSystem).exists(eq(FILE_PATH));
        verifyNoInteractions(remoteFileProcessor);
        verifyNoInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateWhenHeadRequestReturnInvalidHead() {
        // given
        remoteFileSyncer = new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx);

        givenTriggerUpdate();

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.getHeader(HttpHeaders.CONTENT_LENGTH))
                .willReturn("notnumber");

        // when
        remoteFileSyncer.sync();

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH));
        verify(httpClient).request(any());
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(), any());
        verify(vertx).setPeriodic(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateWhenPropsIsFailed() {
        // given
        remoteFileSyncer = new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx);

        givenTriggerUpdate();

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString()))
                .willReturn(Future.failedFuture(new IllegalArgumentException("ERROR")));

        // when
        remoteFileSyncer.sync();

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH));
        verify(httpClient).request(any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH));
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(CopyOptions.class));
        verify(vertx).setPeriodic(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldNotUpdateServiceWhenSizeEqualsContentLength() {
        // given
        remoteFileSyncer = new RemoteFileSyncer(remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx);

        givenTriggerUpdate();

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString()))
                .willReturn(Future.succeededFuture(fileProps));

        doReturn(FILE_SIZE).when(fileProps).size();

        // when
        remoteFileSyncer.sync();

        // then
        verify(fileSystem, times(2)).exists(eq(FILE_PATH));
        verify(httpClient).request(any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH));
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem, never()).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(CopyOptions.class));
        verify(vertx).setPeriodic(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldUpdateServiceWhenSizeNotEqualsContentLength() {
        // given
        remoteFileSyncer = new RemoteFileSyncer(
                remoteFileProcessor, SOURCE_URL, FILE_PATH, TMP_FILE_PATH, RETRY_POLICY,
                TIMEOUT, UPDATE_INTERVAL, httpClient, vertx);

        givenTriggerUpdate();

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.pipeTo(any())).willReturn(Future.succeededFuture());
        given(httpClientResponse.getHeader(any(CharSequence.class))).willReturn(FILE_SIZE.toString());

        given(fileSystem.props(anyString()))
                .willReturn(Future.succeededFuture(fileProps));

        given(fileSystem.delete(anyString()))
                .willReturn(Future.succeededFuture());

        doReturn(123L).when(fileProps).size();

        given(fileSystem.open(anyString(), any()))
                .willReturn(Future.succeededFuture(asyncFile));

        given(fileSystem.move(anyString(), any(), any(CopyOptions.class)))
                .willReturn(Future.succeededFuture());

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        // when
        remoteFileSyncer.sync();

        // then
        verify(httpClient, times(2)).request(any());
        verify(httpClientResponse).getHeader(eq(HttpHeaders.CONTENT_LENGTH));
        verify(fileSystem).props(eq(FILE_PATH));

        // Download
        verify(fileSystem).open(eq(TMP_FILE_PATH), any());
        verify(asyncFile).close();

        verify(remoteFileProcessor, times(2)).setDataPath(any());
        verify(vertx).setPeriodic(eq(UPDATE_INTERVAL), any());
        verify(fileSystem).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(CopyOptions.class));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldRetryAfterFailedDownload() {
        // given
        given(fileSystem.exists(any()))
                .willReturn(Future.succeededFuture(false));

        given(fileSystem.open(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException()));
        given(fileSystem.delete(any()))
                .willReturn(Future.succeededFuture());

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        // when
        remoteFileSyncer.sync();

        // then
        verify(vertx, times(RETRY_COUNT)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(TMP_FILE_PATH), any());

        verifyNoInteractions(httpClient);
        verifyNoInteractions(remoteFileProcessor);
    }

    @Test
    public void syncForFilepathShouldRetryWhenFileOpeningFailed() {
        // then
        given(fileSystem.exists(any()))
                .willReturn(Future.succeededFuture(false));

        given(fileSystem.open(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException()));
        given(fileSystem.delete(any()))
                .willReturn(Future.succeededFuture());

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        given(fileSystem.delete(any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture(new RuntimeException())));

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        // when
        remoteFileSyncer.sync();

        // then
        verify(vertx, times(RETRY_COUNT)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).delete(eq(TMP_FILE_PATH));

        verifyNoInteractions(httpClient);
        verifyNoInteractions(remoteFileProcessor);
    }

    @Test
    public void syncForFilepathShouldDownloadFilesAndNotUpdateWhenUpdatePeriodIsNotSet() {
        // given
        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        given(fileSystem.exists(anyString()))
                .willReturn(Future.succeededFuture(false));

        given(fileSystem.open(any(), any()))
                .willReturn(Future.succeededFuture(asyncFile));

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.pipeTo(asyncFile))
                .willReturn(Future.succeededFuture());

        given(fileSystem.move(anyString(), anyString(), any(CopyOptions.class)))
                .willReturn(Future.succeededFuture());

        // when
        remoteFileSyncer.sync();

        // then
        verify(fileSystem).open(eq(TMP_FILE_PATH), any());
        verify(httpClient).request(any());
        verify(asyncFile).close();
        verify(remoteFileProcessor).setDataPath(any());
        verify(fileSystem).move(eq(TMP_FILE_PATH), eq(FILE_PATH), any(CopyOptions.class));
        verify(vertx, never()).setTimer(eq(UPDATE_INTERVAL), any());
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    public void syncForFilepathShouldRetryWhenTimeoutIsReached() {
        // given
        given(fileSystem.exists(anyString()))
                .willReturn(Future.succeededFuture(false));

        given(fileSystem.open(anyString(), any()))
                .willReturn(Future.succeededFuture(asyncFile));

        given(vertx.setTimer(eq(RETRY_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(0L, 10L, 1));

        given(fileSystem.delete(any()))
                .willReturn(Future.succeededFuture());

        given(httpClient.request(any()))
                .willReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send())
                .willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientResponse.pipeTo(asyncFile))
                .willReturn(Future.failedFuture("Timeout"));

        // when
        remoteFileSyncer.sync();

        // then
        verify(vertx, times(RETRY_COUNT)).setTimer(eq(RETRY_INTERVAL), any());
        verify(fileSystem, times(RETRY_COUNT + 1)).open(eq(TMP_FILE_PATH), any());

        // Response handled
        verify(httpClient, times(RETRY_COUNT + 1)).request(any());
        verify(asyncFile, times(RETRY_COUNT + 1)).close();

        verifyNoInteractions(remoteFileProcessor);
    }

    private void givenTriggerUpdate() {
        given(fileSystem.exists(anyString()))
                .willReturn(Future.succeededFuture(true));

        given(remoteFileProcessor.setDataPath(anyString())).willReturn(Future.succeededFuture());

        given(vertx.setPeriodic(eq(UPDATE_INTERVAL), any()))
                .willAnswer(withReturnObjectAndPassObjectToHandler(123L, 123L, 1))
                .willReturn(123L);
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
