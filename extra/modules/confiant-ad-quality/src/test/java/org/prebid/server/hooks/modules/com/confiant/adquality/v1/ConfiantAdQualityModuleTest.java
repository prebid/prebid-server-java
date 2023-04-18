package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfiantAdQualityModuleTest {

    @Test
    public void shouldHaveValidInitialConfigs() {
        // given

        // when

        // then
        assertThat(ConfiantAdQualityModule.CODE).isEqualTo("confiant-ad-quality");
    }
}
