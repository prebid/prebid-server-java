package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ChannelFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelFunction target = new ChannelFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenArgumentsArePresent() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("args", TextNode.valueOf("args"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("No arguments allowed");
    }

    @Test
    public void extractShouldReturnChannel() {
        // given
        final ExtRequest ext = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("channel"))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().ext(ext).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("channel");
    }

    @Test
    public void extractShouldReturnWebWhenChannelIsPbjs() {
        // given
        final ExtRequest ext = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("pbjs"))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().ext(ext).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("web");
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
