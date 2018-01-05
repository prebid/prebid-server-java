package org.rtb.vexing.adapter;

import io.vertx.core.http.HttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.adapter.appnexus.AppnexusAdapter;
import org.rtb.vexing.adapter.facebook.FacebookAdapter;
import org.rtb.vexing.adapter.pulsepoint.PulsepointAdapter;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;
import org.rtb.vexing.config.ApplicationConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class AdapterCatalogTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig applicationConfig;
    @Mock
    private HttpClient httpClient;

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> AdapterCatalog.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> AdapterCatalog.create(applicationConfig, null));
    }

    @Test
    public void getShouldReturnConfiguredAdapter() {
        // given
        given(applicationConfig.getString(eq("external_url"))).willReturn("http://external-url");
        given(applicationConfig.getLong(eq("default-timeout-ms"))).willReturn(250L);

        given(applicationConfig.getString(eq("adapters.rubicon.endpoint"))).willReturn("http://rubiconproject.com/x");
        given(applicationConfig.getString(eq("adapters.rubicon.usersync_url")))
                .willReturn("http://rubiconproject.com/x/cookie/x");
        given(applicationConfig.getString(eq("adapters.rubicon.XAPI.Username"))).willReturn("rubicon_user");
        given(applicationConfig.getString(eq("adapters.rubicon.XAPI.Password"))).willReturn("rubicon_password");

        given(applicationConfig.getString(eq("adapters.appnexus.endpoint"))).willReturn("http://appnexus-endpoint");
        given(applicationConfig.getString(eq("adapters.appnexus.usersync_url")))
                .willReturn("http://appnexus-usersync-url");

        given(applicationConfig.getString(eq("adapters.facebook.endpoint"))).willReturn("http://facebook-endpoint");
        given(applicationConfig.getString(eq("adapters.facebook.nonSecureEndpoint")))
                .willReturn("http://facebook-endpoint");
        given(applicationConfig.getString(eq("adapters.facebook.usersync_url")))
                .willReturn("http://facebook-usersync-url");
        given(applicationConfig.getString(eq("adapters.facebook.platform_id"))).willReturn("42");

        given(applicationConfig.getString(eq("adapters.pulsepoint.endpoint"))).willReturn("http://pulsepoint-endpoint");
        given(applicationConfig.getString(eq("adapters.pulsepoint.usersync_url")))
                .willReturn("http://pulsepoint-usersync-url");

        // when
        final AdapterCatalog adapterCatalog = AdapterCatalog.create(applicationConfig, httpClient);

        // then
        assertThat(adapterCatalog.getByCode("rubicon"))
                .isNotNull()
                .isInstanceOf(RubiconAdapter.class);

        assertThat(adapterCatalog.getByCode("appnexus"))
                .isNotNull()
                .isInstanceOf(AppnexusAdapter.class);

        assertThat(adapterCatalog.getByCode("audienceNetwork"))
                .isNotNull()
                .isInstanceOf(FacebookAdapter.class);

        assertThat(adapterCatalog.getByCode("pulsepoint"))
                .isNotNull()
                .isInstanceOf(PulsepointAdapter.class);
    }
}
