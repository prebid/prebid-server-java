package org.prebid.server.execution.file.supplier;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.assertion.FutureAssertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RemoteFileSupplierTest {

    private static final String SAVE_PATH = "/path/to/file";
    private static final String BACKUP_PATH = SAVE_PATH + ".old";
    private static final String TMP_PATH = "/path/to/tmp";

    @Mock
    private HttpClient httpClient;

    @Mock
    private FileSystem fileSystem;

    private RemoteFileSupplier target;

    @Mock
    private HttpClientResponse getResponse;

    @Mock
    private HttpClientResponse headResponse;

    @BeforeEach
    public void setUp() {
        final HttpClientRequest getRequest = mock(HttpClientRequest.class);
        given(httpClient.request(argThat(requestOptions ->
                requestOptions != null && requestOptions.getMethod().equals(HttpMethod.GET))))
                .willReturn(Future.succeededFuture(getRequest));
        given(getRequest.send()).willReturn(Future.succeededFuture(getResponse));

        final HttpClientRequest headRequest = mock(HttpClientRequest.class);
        given(httpClient.request(argThat(requestOptions ->
                requestOptions != null && requestOptions.getMethod().equals(HttpMethod.HEAD))))
                .willReturn(Future.succeededFuture(headRequest));
        given(headRequest.send()).willReturn(Future.succeededFuture(headResponse));
        given(headResponse.statusCode()).willReturn(200);

        target = target(false);
    }

    private RemoteFileSupplier target(boolean checkRemoteFileSize) {
        return new RemoteFileSupplier(
                "https://download.url/",
                SAVE_PATH,
                TMP_PATH,
                httpClient,
                1000L,
                checkRemoteFileSize,
                fileSystem);
    }

    @Test
    public void shouldCheckWritePermissionsForFiles() {
        // given
        reset(fileSystem);
        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.existsBlocking(anyString())).willReturn(true);
        given(fileSystem.propsBlocking(anyString())).willReturn(fileProps);
        given(fileProps.isDirectory()).willReturn(false);

        // when
        target(false);

        // then
        verify(fileSystem, times(3)).mkdirsBlocking(anyString());
    }

    @Test
    public void getShouldReturnFailureWhenCanNotOpenTmpFile() {
        // given
        given(fileSystem.open(eq(TMP_PATH), any())).willReturn(Future.failedFuture("Failure."));
        given(fileSystem.exists(eq(SAVE_PATH))).willReturn(Promise.<Boolean>promise().future());

        // when
        final Future<String> result = target.get();

        // then
        FutureAssertion.assertThat(result).isFailed().hasMessage("Failure.");
    }

    @Test
    public void getShouldReturnFailureOnNotOkStatusCode() {
        // given
        final AsyncFile tmpFile = mock(AsyncFile.class);
        given(fileSystem.open(eq(TMP_PATH), any())).willReturn(Future.succeededFuture(tmpFile));
        given(fileSystem.exists(eq(SAVE_PATH))).willReturn(Promise.<Boolean>promise().future());

        given(getResponse.statusCode()).willReturn(204);

        // when
        final Future<String> result = target.get();

        // then
        FutureAssertion.assertThat(result).isFailed()
                .hasMessage("Got unexpected response from server with status code 204 and message null");
    }

    @Test
    public void getShouldReturnExpectedResult() {
        // given
        final AsyncFile tmpFile = mock(AsyncFile.class);
        given(fileSystem.open(eq(TMP_PATH), any())).willReturn(Future.succeededFuture(tmpFile));
        given(fileSystem.exists(eq(SAVE_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.move(eq(SAVE_PATH), eq(BACKUP_PATH), Mockito.<CopyOptions>any()))
                .willReturn(Future.succeededFuture());
        given(fileSystem.move(eq(TMP_PATH), eq(SAVE_PATH), Mockito.<CopyOptions>any()))
                .willReturn(Future.succeededFuture());

        given(getResponse.statusCode()).willReturn(200);
        given(getResponse.pipeTo(any())).willReturn(Future.succeededFuture());

        // when
        final Future<String> result = target.get();

        // then
        verify(tmpFile).close();
        assertThat(result.result()).isEqualTo(SAVE_PATH);
    }

    @Test
    public void getShouldReturnExpectedResultWhenCheckRemoteFileSizeIsTrue() {
        // given
        target = target(true);

        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.exists(eq(SAVE_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.props(eq(SAVE_PATH))).willReturn(Future.succeededFuture(fileProps));
        given(fileProps.size()).willReturn(1000L);

        given(headResponse.statusCode()).willReturn(200);
        given(headResponse.getHeader(eq(HttpHeaders.CONTENT_LENGTH))).willReturn("1001");

        final AsyncFile tmpFile = mock(AsyncFile.class);
        given(fileSystem.open(eq(TMP_PATH), any())).willReturn(Future.succeededFuture(tmpFile));
        given(fileSystem.move(eq(SAVE_PATH), eq(BACKUP_PATH), Mockito.<CopyOptions>any()))
                .willReturn(Future.succeededFuture());
        given(fileSystem.move(eq(TMP_PATH), eq(SAVE_PATH), Mockito.<CopyOptions>any()))
                .willReturn(Future.succeededFuture());

        given(getResponse.statusCode()).willReturn(200);
        given(getResponse.pipeTo(any())).willReturn(Future.succeededFuture());

        // when
        final Future<String> result = target.get();

        // then
        verify(tmpFile).close();
        assertThat(result.result()).isEqualTo(SAVE_PATH);
    }

    @Test
    public void getShouldReturnNullWhenCheckRemoteFileSizeIsTrueAndSizeNotChanged() {
        // given
        target = target(true);

        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.exists(eq(SAVE_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.props(eq(SAVE_PATH))).willReturn(Future.succeededFuture(fileProps));
        given(fileProps.size()).willReturn(1000L);

        given(headResponse.statusCode()).willReturn(200);
        given(headResponse.getHeader(eq(HttpHeaders.CONTENT_LENGTH))).willReturn("1000");

        // when
        final Future<String> result = target.get();

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void clearTmpShouldCallExpectedMethods() {
        // given
        given(fileSystem.exists(eq(TMP_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.delete(eq(TMP_PATH))).willReturn(Future.succeededFuture());

        // when
        target.clearTmp();

        // then
        verify(fileSystem).delete(TMP_PATH);
    }

    @Test
    public void deleteBackupShouldCallExpectedMethods() {
        // given
        given(fileSystem.exists(eq(BACKUP_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.delete(eq(BACKUP_PATH))).willReturn(Future.succeededFuture());

        // when
        target.deleteBackup();

        // then
        verify(fileSystem).delete(BACKUP_PATH);
    }

    @Test
    public void restoreFromBackupShouldCallExpectedMethods() {
        // given
        given(fileSystem.exists(eq(BACKUP_PATH))).willReturn(Future.succeededFuture(true));
        given(fileSystem.move(eq(BACKUP_PATH), eq(SAVE_PATH))).willReturn(Future.succeededFuture());
        given(fileSystem.delete(eq(BACKUP_PATH))).willReturn(Future.succeededFuture());

        // when
        target.deleteBackup();

        // then
        verify(fileSystem).delete(BACKUP_PATH);
    }
}
