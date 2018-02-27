package org.prebid.server.adapter;

import org.junit.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class AdapterCatalogTest {

    private static final String ADAPTER = "rubicon";

    @Test
    public void constructorShouldFailONullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(null));
    }

    @Test
    public void getByCodeShouldReturnAdapterWhenAdapterExist() {
        // given
        final Adapter adapter = mock(Adapter.class);
        given(adapter.name()).willReturn(ADAPTER);

        final AdapterCatalog catalog = new AdapterCatalog(singletonList(adapter));

        // when
        final Adapter result = catalog.byName(ADAPTER);

        // then
        assertThat(result.name()).isEqualTo(ADAPTER);
    }

    @Test
    public void getByCodeShouldReturnNullWhenAdapterNotExist() {
        // given
        final AdapterCatalog catalog = new AdapterCatalog(emptyList());

        // when
        final Adapter result = catalog.byName(ADAPTER);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void isValidCodeShouldReturnFalseWhenAdapterNotExist() {
        // given
        final AdapterCatalog catalog = new AdapterCatalog(emptyList());

        // when
        final Boolean result = catalog.isValidName(ADAPTER);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isValidCodeShouldReturnTrueWhenAdapterExist() {
        // given
        final Adapter adapter = mock(Adapter.class);
        given(adapter.name()).willReturn(ADAPTER);

        final AdapterCatalog catalog = new AdapterCatalog(singletonList(adapter));

        // when
        final Boolean result = catalog.isValidName(ADAPTER);

        // then
        assertThat(result).isTrue();
    }
}
