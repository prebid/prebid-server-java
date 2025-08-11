package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PrebidKeyFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final PrebidKeyFunction target = new PrebidKeyFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'key' is required and has to be a string");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenKeyFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'key' is required and has to be a string");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenKeyFieldIsNotAString() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("key", IntNode.valueOf(1));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'key' is required and has to be a string");
    }

    @Test
    public void extractShouldReturnExtPrebidKvpsValueBySpecifiedKey() {
        // given
        final ObjectNode extPrebidKvpsNode = mapper.createObjectNode().set("key", TextNode.valueOf("value"));
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().kvps(extPrebidKvpsNode).build()))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "key");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("value");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenExtPrebidKvpsValueBySpecifiedKeyIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "key");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("undefined");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String key) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithKey(key),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }

    private ObjectNode givenConfigWithKey(String key) {
        return mapper.createObjectNode().set("key", TextNode.valueOf(key));
    }
}
