package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1;

import org.junit.Test;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FiftyOneDeviceDetectionModuleTest {
    @Test
    public void shouldReturnNonBlankCode() {

        // given
        final Module module = new FiftyOneDeviceDetectionModule(null);

        // when and then
        assertThat(module.code()).isNotBlank();
    }

    @Test
    public void shouldReturnSavedHooks() {

        // given
        final Collection<Hook<?, ? extends InvocationContext>> hooks = Collections.emptyList();
        final Module module = new FiftyOneDeviceDetectionModule(hooks);

        // when and then
        assertThat(module.hooks()).isEqualTo(hooks);
    }
}
