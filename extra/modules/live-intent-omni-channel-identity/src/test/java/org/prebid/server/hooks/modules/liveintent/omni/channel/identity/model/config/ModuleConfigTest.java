package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleConfigTest {

    @Test
    public void shouldReturnRequestTimeoutMs() {
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setRequestTimeoutMs(5);
        assertThat(moduleConfig.getRequestTimeoutMs()).isEqualTo(5);
    }

    @Test
    public void shouldReturnIdentityResolutionEndpoint() {
        // given
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setIdentityResolutionEndpoint("https://test.com/idres");

        // when and then
        assertThat(moduleConfig.getIdentityResolutionEndpoint()).isEqualTo("https://test.com/idres");
    }

    @Test
    public void shouldReturnAuthToken() {
        // given
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setAuthToken("secret_token");

        // when and then
        assertThat(moduleConfig.getAuthToken()).isEqualTo("secret_token");
    }
}
