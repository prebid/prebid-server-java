package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountActivityGeoRuleConfigMatcherTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    private final AccountActivityGeoRuleConfigMatcher target = new AccountActivityGeoRuleConfigMatcher();

    @Test
    public void matchesShouldReturnFalseOnNull() {
        // when
        final boolean result = target.matches(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseOnWrongNodeType() {
        // when
        final boolean result = target.matches(TextNode.valueOf(""));

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseIfConditionPropertyNotFound() {
        // when
        final boolean result = target.matches(mapper.createObjectNode());

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseOnWrongNodeTypeOfConditionProperty() {
        // given
        final ObjectNode config = mapper.createObjectNode();
        config.put("condition", 1);

        // when
        final boolean result = target.matches(config);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseIfExpectedPropertyNotFound() {
        // given
        final ObjectNode config = mapper.createObjectNode();
        config.putObject("condition");

        // when
        final boolean result = target.matches(config);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void matchesShouldReturnTrueOnConfigWithGppSid() {
        // given
        final ObjectNode config = mapper.createObjectNode();
        config.putObject("condition").put("gppSid", 1);

        // when
        final boolean result = target.matches(config);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void matchesShouldReturnTrueOnConfigWithGeo() {
        // given
        final ObjectNode config = mapper.createObjectNode();
        config.putObject("condition").put("geo", 1);

        // when
        final boolean result = target.matches(config);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void matchesShouldReturnTrueOnConfigWithGpc() {
        // given
        final ObjectNode config = mapper.createObjectNode();
        config.putObject("condition").put("gpc", 1);

        // when
        final boolean result = target.matches(config);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void typeShouldReturnExpectedResult() {
        // when
        final Class<? extends AccountActivityRuleConfig> result = target.type();

        // then
        assertThat(result).isEqualTo(AccountActivityGeoRuleConfig.class);
    }
}
