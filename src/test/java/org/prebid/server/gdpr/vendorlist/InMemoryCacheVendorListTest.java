package org.prebid.server.gdpr.vendorlist;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class InMemoryCacheVendorListTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private VendorList delegate;

    private InMemoryCacheVendorList inMemoryCacheVendorList;

    @Before
    public void setUp() {
        given(delegate.forVersion(anyInt(), any()))
                .willReturn(Future.succeededFuture(VendorListInfo.of(0, null, null)));

        inMemoryCacheVendorList = new InMemoryCacheVendorList(1, delegate);
    }

    @Test
    public void shouldReturnVendorListFromCache() {
        // when
        inMemoryCacheVendorList.forVersion(1, null); // filling cache
        final Future<?> future = inMemoryCacheVendorList.forVersion(1, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(VendorListInfo.of(0, null, null));
        verify(delegate).forVersion(eq(1), eq(null));
    }

    @Test
    public void shouldAskDelegateWithExpectedArgumentsIfVendorListNotFound() {
        // when
        inMemoryCacheVendorList.forVersion(1, null);

        // then
        verify(delegate).forVersion(eq(1), eq(null));
    }
}
