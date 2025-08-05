package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DeviceCountryFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DeviceCountryFunction target = new DeviceCountryFunction();

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
    public void extractShouldReturnDeviceCountry() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("country").build()).build())
                .build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("country");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenChannelIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("undefined");
    }
}
