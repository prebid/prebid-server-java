package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeviceCountryInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DeviceCountryInFunction target = new DeviceCountryInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("countries", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode countriesNode = mapper.createArrayNode();
        countriesNode.add(TextNode.valueOf("test"));
        countriesNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("countries", countriesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenDeviceCountryPresentInConfiguredCountries() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("country").build()).build())
                .build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                givenConfigWithCountries("country"));

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenDeviceCountryIsAbsentInConfiguredCountries() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                givenConfigWithCountries("expectedCountry"));

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private ObjectNode givenConfigWithCountries(String... countries) {
        final ArrayNode countriesNode = mapper.createArrayNode();
        Arrays.stream(countries).map(TextNode::valueOf).forEach(countriesNode::add);
        return mapper.createObjectNode().set("countries", countriesNode);
    }
}
