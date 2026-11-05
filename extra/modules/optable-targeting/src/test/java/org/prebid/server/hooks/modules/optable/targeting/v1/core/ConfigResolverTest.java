package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigResolverTest extends BaseOptableTest {

    private OptableTargetingProperties globalProperties;

    private ConfigResolver target;

    @BeforeEach
    public void setUp() {
        globalProperties = givenOptableTargetingProperties(false);
        target = new ConfigResolver(mapper, jsonMerger, globalProperties);
    }

    @Test
    public void resolveShouldReturnGlobalPropertiesWhenAccountHasNoModuleConfig() {
        // when and then
        assertThat(target.resolve(null)).isSameAs(globalProperties);
    }

    @Test
    public void resolveShouldPreferAccountPropertiesOverGlobalOnes() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode().put("tenant", "accountTenant");

        // when
        final OptableTargetingProperties result = target.resolve(accountConfig);

        // then
        assertThat(result.getTenant()).isEqualTo("accountTenant");
        assertThat(result.getOrigin()).isEqualTo(globalProperties.getOrigin());
    }
}
