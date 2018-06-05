package org.prebid.server.gdpr.vendorlist;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FileCacheVendorListTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;
    @Mock
    private VendorList delegate;

    private FileCacheVendorList fileCacheVendorList;

    @Before
    public void setUp() {
        given(delegate.forVersion(anyInt(), any()))
                .willReturn(Future.succeededFuture(VendorListInfo.of(0, null, null)));

        fileCacheVendorList = new FileCacheVendorList(fileSystem, "/cache/dir", delegate);
    }

    @Test
    public void shouldReturnVendorListFromCache() {
        // given
        given(fileSystem.existsBlocking(anyString())).willReturn(true);
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("{}"));

        // when
        final Future<?> future = fileCacheVendorList.forVersion(1, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(VendorListInfo.of(0, null, null));
        verifyZeroInteractions(delegate);
    }

    @Test
    public void shouldAskDelegateWithExpectedArgumentsIfVendorListNotFound() {
        // given
        given(fileSystem.existsBlocking(anyString())).willReturn(false);

        // when
        fileCacheVendorList.forVersion(1, null);

        // then
        verify(delegate).forVersion(eq(1), eq(null));
    }

    @Test
    public void shouldSaveToCacheIfVendorListObtainedFromDelegate() {
        // given
        given(fileSystem.existsBlocking(anyString())).willReturn(false);

        // when
        fileCacheVendorList.forVersion(1, null);

        // then
        verify(fileSystem).writeFileBlocking(eq("/cache/dir/1.json"), eq(Buffer.buffer("{\"vendorListVersion\":0}")));
    }

    @Test
    public void shouldFailIfErrorOccurredWhileSavingToCache() {
        // given
        given(fileSystem.existsBlocking(anyString())).willReturn(false);
        given(fileSystem.writeFileBlocking(anyString(), any())).willThrow(new RuntimeException("exception"));

        // when
        final Future<?> future = fileCacheVendorList.forVersion(1, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("exception");
    }
}
