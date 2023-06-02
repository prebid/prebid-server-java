package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityGppSidRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountActivityRuleConfigResolverTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    @Test
    public void matchesShouldReturnGppSidRuleTypeForCertainConfig() {
        //given
        final ObjectNode config = mapper.createObjectNode();
        config.put("gppSid", 1);

        // when
        final Class<? extends AccountActivityRuleConfig> result = AccountActivityRuleConfigResolver.resolve(config);

        // then
        assertThat(result).isEqualTo(AccountActivityGppSidRuleConfig.class);
    }

    @Test
    public void matchesShouldReturnComponentRuleTypeByDefault() {
        // when
        final Class<? extends AccountActivityRuleConfig> result = AccountActivityRuleConfigResolver.resolve(null);

        // then
        assertThat(result).isEqualTo(AccountActivityComponentRuleConfig.class);
    }
}
