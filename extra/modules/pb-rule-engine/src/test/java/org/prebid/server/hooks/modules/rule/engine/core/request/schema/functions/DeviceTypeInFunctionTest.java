package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeviceTypeInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DeviceTypeInFunction target = new DeviceTypeInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("types", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsNotAnArrayOfIntegers() {
        // given
        final ArrayNode typesNode = mapper.createArrayNode();
        typesNode.add(TextNode.valueOf("test"));
        typesNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("types", typesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of integers");
    }

    @Test
    public void extractShouldReturnTrueWhenDeviceTypePresentInConfiguredTypes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().devicetype(12345).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, 12345);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenDeviceTypeIsAbsentInConfiguredTypes() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, 1);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            int... types) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithTypes(types),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }

    private ObjectNode givenConfigWithTypes(int... types) {
        final ArrayNode typesNode = mapper.createArrayNode();
        Arrays.stream(types).mapToObj(IntNode::valueOf).forEach(typesNode::add);
        return mapper.createObjectNode().set("types", typesNode);
    }
}
