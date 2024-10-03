package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GreenbidsRealTimeDataModuleTest {

    @Test
    public void shouldHaveValidInitialConfigs() {
        // given and when and then
        assertThat(GreenbidsRealTimeDataModule.CODE).isEqualTo("greenbids-real-time-data");
    }
}
