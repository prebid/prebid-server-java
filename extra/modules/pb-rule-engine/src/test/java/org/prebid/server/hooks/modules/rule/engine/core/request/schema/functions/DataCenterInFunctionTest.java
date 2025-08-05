package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DataCenterInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DataCenterInFunction target = new DataCenterInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'datacenters' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'datacenters' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("datacenters", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'datacenters' is required and has to be an array of strings");
    }

    private ObjectNode givenConfigWithDataCenters(String... dataCenters) {
        final ArrayNode dataCentersNode = mapper.createArrayNode();
        Arrays.stream(dataCenters).map(TextNode::valueOf).forEach(dataCentersNode::add);
        return mapper.createObjectNode().set("bundles", dataCentersNode);
    }
}
