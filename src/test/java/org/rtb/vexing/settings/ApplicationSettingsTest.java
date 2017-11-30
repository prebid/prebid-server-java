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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class ApplicationSettingsTest {

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
        assertThatNullPointerException().isThrownBy(() -> ApplicationSettings.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> ApplicationSettings.create(vertx, null));
    }

    @Test
    public void createShouldReturnFileApplicationProperties() {
        // given
        given(vertx.fileSystem()).willReturn(fileSystem);
        given(fileSystem.readFileBlocking(any())).willReturn(Buffer.buffer("accounts:"));
        given(applicationConfig.getString(eq("datacache.type"))).willReturn("filecache");
        given(applicationConfig.getString(eq("datacache.filename"))).willReturn("ignored");

        // when
        final Future<? extends ApplicationSettings> future = ApplicationSettings.create(vertx, applicationConfig);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isInstanceOf(FileApplicationSettings.class);
    }
}
