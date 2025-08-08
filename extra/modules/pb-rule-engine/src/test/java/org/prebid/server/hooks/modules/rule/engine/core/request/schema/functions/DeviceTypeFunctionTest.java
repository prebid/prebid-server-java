package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeviceTypeFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DeviceTypeFunction target = new DeviceTypeFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenArgumentsArePresent() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("args", TextNode.valueOf("args"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("No arguments allowed");
    }

    @Test
    public void extractShouldReturnDeviceType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().devicetype(12345).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("12345");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenChannelIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("undefined");
    }

    private static SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest) {

        return SchemaFunctionArguments.of(
                bidRequest,
                null,
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }
}
