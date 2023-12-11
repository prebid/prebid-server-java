package org.prebid.server.hooks.modules.pb.richmedia.filter.v1;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PbRichmediaFilterModuleTest {

    @Test
    public void shouldHaveValidInitialConfigs() {
        // given and when and then
        assertThat(PbRichmediaFilterModule.CODE).isEqualTo("pb-richmedia-filter");
    }

}
