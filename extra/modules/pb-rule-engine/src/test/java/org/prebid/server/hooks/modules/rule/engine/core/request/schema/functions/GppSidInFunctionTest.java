package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GppSidInFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GppSidInFunction target = new GppSidInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sids' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenSidsFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sids' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenSidsFieldIsNotAnArray() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("sids", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sids' is required and has to be an array of integers");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenSidsFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode sidsNode = MAPPER.createArrayNode();
        sidsNode.add(TextNode.valueOf("test"));
        sidsNode.add(IntNode.valueOf(1));
        final ObjectNode config = MAPPER.createObjectNode().set("sids", sidsNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sids' is required and has to be an array of integers");
    }

    @Test
    public void extractShouldReturnTrueWhenAnyOfRegsGppSidIsPresentInConfiguredSids() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.builder().gppSid(Collections.singletonList(1)).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, 1);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenAllOfRegsGppSidAreAbsentConfiguredSids() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.builder().gppSid(Collections.singletonList(2)).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, 1);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            com.iab.openrtb.request.BidRequest bidRequest,
            int... sids) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithSids(sids),
                RequestRuleContext.of(
                        AuctionContext.builder().build(),
                        Granularity.Request.instance(),
                        "datacenter"));
    }

    private ObjectNode givenConfigWithSids(int... sids) {
        final ArrayNode sidsNode = MAPPER.createArrayNode();
        Arrays.stream(sids).mapToObj(IntNode::valueOf).forEach(sidsNode::add);
        return MAPPER.createObjectNode().set("sids", sidsNode);
    }
}
