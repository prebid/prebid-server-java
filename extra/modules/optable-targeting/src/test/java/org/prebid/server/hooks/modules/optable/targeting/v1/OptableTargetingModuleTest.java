package org.prebid.server.hooks.modules.optable.targeting.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.optable.targeting.v1.analytics.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingModuleTest {

    @Mock
    ConfigResolver configResolver;

    @Mock
    OptableTargeting optableTargeting;

    @Mock
    PayloadResolver payloadResolver;

    @Mock
    OptableAttributesResolver optableAttributesResolver;

    @Mock
    AnalyticTagsResolver analyticTagsResolver;

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
                List.of(new OptableTargetingProcessedAuctionRequestHook(
                        configResolver,
                        optableTargeting,
                        payloadResolver,
                        optableAttributesResolver),
                        new OptableTargetingAuctionResponseHook(
                                analyticTagsResolver,
                                payloadResolver,
                                configResolver));

        final Module module = new OptableTargetingModule(hooks);

        // when and then
        assertThat(module.hooks())
                .hasSize(2)
                .isEqualTo(hooks);
    }
}
