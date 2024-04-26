package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountActivityDefaultRuleConfigMatcherTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    private final AccountActivityDefaultRuleConfigMatcher target = new AccountActivityDefaultRuleConfigMatcher();

    @Test
    public void matchesShouldAlwaysReturnTrue() {
        // when
        final boolean result = target.matches(mapper.createObjectNode());

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void typeShouldReturnExpectedResult() {
        // when
        final Class<? extends AccountActivityRuleConfig> result = target.type();

        // then
        assertThat(result).isEqualTo(AccountActivityComponentRuleConfig.class);
    }
}
