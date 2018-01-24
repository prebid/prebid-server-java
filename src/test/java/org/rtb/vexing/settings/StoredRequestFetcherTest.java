package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.config.ApplicationConfig;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class StoredRequestFetcherTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private FileSystem fileSystem;
    @Mock
    private ApplicationConfig applicationConfig;

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> StoredRequestFetcher.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> StoredRequestFetcher.create(vertx, null));
    }

    @Test
    public void createShouldReturnFileStoredRequestFetcher() {
        // given
        given(applicationConfig.getString(eq("stored_requests.type"))).willReturn("filesystem");
        given(applicationConfig.getString(eq("stored_requests.configpath"))).willReturn("configPath");
        given(vertx.fileSystem()).willReturn(fileSystem);
        given(fileSystem.readDirBlocking("configPath")).willReturn(singletonList("1.json"));
        given(fileSystem.readFileBlocking("1.json")).willReturn(Buffer.buffer("value1"));

        // when
        Future<? extends StoredRequestFetcher> storedRequestFetcherFuture =
                StoredRequestFetcher.create(vertx, applicationConfig);

        // then
        assertThat(storedRequestFetcherFuture.succeeded()).isTrue();
        assertThat(storedRequestFetcherFuture.result()).isInstanceOf(FileStoredRequestFetcher.class);
    }
}
