package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingModuleTest {

    @Mock
    ConfigResolver configResolver;

    @Mock
    OptableTargeting optableTargeting;

    @Mock(strictness = LENIENT)
    UserFpdActivityMask userFpdActivityMask;

    ObjectMapper mapper = ObjectMapperProvider.mapper();

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
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        final Collection<Hook<?, ? extends InvocationContext>> hooks =
                List.of(new OptableTargetingProcessedAuctionRequestHook(
                        configResolver,
                        optableTargeting,
                        userFpdActivityMask),
                        new OptableTargetingAuctionResponseHook(
                                configResolver,
                                mapper));

        final Module module = new OptableTargetingModule(hooks);

        // when and then
        assertThat(module.hooks())
                .hasSize(2)
                .isEqualTo(hooks);
    }
}
