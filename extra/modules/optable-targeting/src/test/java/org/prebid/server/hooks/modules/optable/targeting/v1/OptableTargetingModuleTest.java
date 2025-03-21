package org.prebid.server.hooks.modules.optable.targeting.v1;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OptableTargetingModuleTest {

    @Test
    public void shouldReturnNonBlankCode() {
        // given
        final Module module = new OptableTargetingModule(null);

        // when and then
        assertThat(module.code())
                .isNotBlank()
                .isEqualTo("optable-targeting");

    }

    @Test
    public void shouldReturnHooks() {
        // given
        final Collection<Hook<?, ? extends InvocationContext>> hooks =
                List.of(new OptableTargetingProcessedAuctionRequestHook(null, null, null, null),
                        new OptableTargetingAuctionResponseHook(null, null, true, null));

        final Module module = new OptableTargetingModule(hooks);

        // when and then
        assertThat(module.hooks())
                .hasSize(2)
                .isEqualTo(hooks);
    }
}
