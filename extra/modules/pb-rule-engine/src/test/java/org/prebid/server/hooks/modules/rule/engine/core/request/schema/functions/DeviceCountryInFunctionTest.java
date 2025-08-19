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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeviceCountryInFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeviceCountryInFunction target = new DeviceCountryInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArray() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("countries", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'countries' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode countriesNode = MAPPER.createArrayNode();
        countriesNode.add(TextNode.valueOf("test"));
        countriesNode.add(IntNode.valueOf(1));
        final ObjectNode config = MAPPER.createObjectNode().set("countries", countriesNode);

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

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "country");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenDeviceCountryIsAbsentInConfiguredCountries() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "expectedCountry");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String... countries) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithCountries(countries),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }

    private ObjectNode givenConfigWithCountries(String... countries) {
        final ArrayNode countriesNode = MAPPER.createArrayNode();
        Arrays.stream(countries).map(TextNode::valueOf).forEach(countriesNode::add);
        return MAPPER.createObjectNode().set("countries", countriesNode);
    }
}
