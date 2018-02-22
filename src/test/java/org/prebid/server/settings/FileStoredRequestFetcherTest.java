package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.StoredRequestResult;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class FileStoredRequestFetcherTest {

    private static final String REQUEST_CONFIG_PATH = "config";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;

    private FileStoredRequestFetcher fileStoredRequestFetcher;

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> FileStoredRequestFetcher.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> FileStoredRequestFetcher.create(REQUEST_CONFIG_PATH, null));
    }

    @Test
    public void shouldReturnResultWithConfigNotFoundErrorForNotExistingId() {
        // given
        given(fileSystem.readDirBlocking(eq(REQUEST_CONFIG_PATH)))
                .willReturn(singletonList("/home/user/requests/1.json"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("value1"));
        fileStoredRequestFetcher = FileStoredRequestFetcher.create(REQUEST_CONFIG_PATH, fileSystem);

        // when
        final Future<StoredRequestResult> storedRequestResult = fileStoredRequestFetcher
                .getStoredRequestsById(singleton("2"), null);
        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        assertThat(storedRequestResult.succeeded()).isTrue();
        assertThat(storedRequestResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No config found for id: 2"));
        assertThat(storedRequestResult.result().getStoredIdToJson()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void shouldReturnResultWithEmptyErrorListIfAllIdsArePresent() {
        // given
        given(fileSystem.readDirBlocking(eq(REQUEST_CONFIG_PATH))).willReturn(singletonList
                ("/home/user/requests/1.json"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("value1"));
        fileStoredRequestFetcher = FileStoredRequestFetcher.create(REQUEST_CONFIG_PATH, fileSystem);

        // when
        final Future<StoredRequestResult> storedRequestResult = fileStoredRequestFetcher
                .getStoredRequestsById(singleton("1"), null);
        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        assertThat(storedRequestResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedRequestResult.result().getStoredIdToJson()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void initializationShouldNotReadFromNonJsonFiles() {
        // given
        given(fileSystem.readDirBlocking(REQUEST_CONFIG_PATH)).willReturn(singletonList("/home/user/requests/1.txt"));
        given(fileSystem.readFileBlocking("2.txt")).willReturn(Buffer.buffer("value2"));

        // when
        fileStoredRequestFetcher = FileStoredRequestFetcher.create(REQUEST_CONFIG_PATH, fileSystem);

        //then
        verify(fileSystem, never()).readFileBlocking("2.txt");
    }
}
