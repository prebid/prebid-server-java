package org.prebid.server.execution.file.supplier;

import io.vertx.core.Future;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.assertion.FutureAssertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class LocalFileSupplierTest {

    @Mock
    private FileSystem fileSystem;

    private LocalFileSupplier target;

    @BeforeEach
    public void setUp() {
        given(fileSystem.exists(anyString())).willReturn(Future.succeededFuture(true));

        target = new LocalFileSupplier("/path/to/file", fileSystem);
    }

    @Test
    public void getShouldReturnFailedFutureIfFileNotFound() {
        // given
        given(fileSystem.exists(anyString())).willReturn(Future.succeededFuture(false));

        // when and then
        FutureAssertion.assertThat(target.get()).isFailed().hasMessage("File /path/to/file not found.");
    }

    @Test
    public void getShouldReturnFilePath() {
        // given
        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.props(anyString())).willReturn(Future.succeededFuture(fileProps));
        given(fileProps.creationTime()).willReturn(1000L);

        // when and then
        assertThat(target.get().result()).isEqualTo("/path/to/file");
    }

    @Test
    public void getShouldReturnNullIfFileNotModifiedSinceLastTry() {
        // given
        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.props(anyString())).willReturn(Future.succeededFuture(fileProps));
        given(fileProps.creationTime()).willReturn(1000L);

        // when
        target.get();
        final Future<String> result = target.get();

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isNull();
    }
}
