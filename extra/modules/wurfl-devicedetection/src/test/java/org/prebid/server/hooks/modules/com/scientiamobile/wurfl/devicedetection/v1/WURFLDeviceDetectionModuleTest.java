package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class WURFLDeviceDetectionModuleTest {

    @Test
    public void codeShouldReturnCorrectModuleCode() {
        // given
        final List<Hook<?, ? extends InvocationContext>> hooks = new ArrayList<>();
        final WURFLDeviceDetectionModule target = new WURFLDeviceDetectionModule(hooks);

        // when
        final String result = target.code();

        // then
        assertThat(result).isEqualTo("wurfl-devicedetection");
    }

    @Test
    public void hooksShouldReturnProvidedHooks() {
        // given
        final List<Hook<?, ? extends InvocationContext>> hooks = new ArrayList<>();
        final WURFLDeviceDetectionModule target = new WURFLDeviceDetectionModule(hooks);

        // when
        final Collection<? extends Hook<?, ? extends InvocationContext>> result = target.hooks();

        // then
        assertThat(result).isSameAs(hooks);
    }
}
