package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    public void validateConfigShouldThrowErrorWhenDatacentersFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode datacentersNode = mapper.createArrayNode();
        datacentersNode.add(TextNode.valueOf("test"));
        datacentersNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("datacenters", datacentersNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'datacenters' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenDataCenterPresentInConfiguredDatacenters() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "datacenter", "datacenter");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenBundleIsAbsentInConfiguredBundles() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "datacenter", "expectedDatacenter");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String datacenter,
            String... expectedDatacenters) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithDataCenters(expectedDatacenters),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), datacenter));
    }

    private ObjectNode givenConfigWithDataCenters(String... dataCenters) {
        final ArrayNode dataCentersNode = mapper.createArrayNode();
        Arrays.stream(dataCenters).map(TextNode::valueOf).forEach(dataCentersNode::add);
        return mapper.createObjectNode().set("datacenters", dataCentersNode);
    }
}
