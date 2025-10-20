package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AdUnitCodeInFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AdUnitCodeInFunction target = new AdUnitCodeInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'codes' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenCodesFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'codes' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenCodesFieldIsNotAnArray() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("codes", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'codes' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenCodesFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode codesNode = MAPPER.createArrayNode();
        codesNode.add(TextNode.valueOf("test"));
        codesNode.add(IntNode.valueOf(1));
        final ObjectNode config = MAPPER.createObjectNode().set("codes", codesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'codes' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenGpidPresentInConfiguredCodes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .ext(MAPPER.createObjectNode().put("gpid", "gpid"))
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "gpid");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenTagidPresentAndGpidIsAbsentInConfiguredCodes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .tagid("tagId")
                .ext(MAPPER.createObjectNode().put("gpid", "gpid"))
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "tagId");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenPbAdSlotPresentAndGpidAndTagidAreAbsentInConfiguredCodes() {
        // given
        final ObjectNode ext = MAPPER.createObjectNode();
        ext.set("data", MAPPER.createObjectNode().put("pbadslot", "pbadslot"));
        ext.set("gpid", TextNode.valueOf("gpid"));

        final Imp imp = Imp.builder()
                .id("impId")
                .tagid("tagId")
                .ext(ext)
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "pbadslot");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenSridPresentAndGpidAndTagidAndPbAdSlotAreAbsentInConfiguredCodes() {
        // given
        final ObjectNode prebid = MAPPER.createObjectNode();
        prebid.set("storedrequest", MAPPER.createObjectNode().put("id", "srid"));
        final ObjectNode ext = MAPPER.createObjectNode();
        ext.set("prebid", prebid);
        ext.set("data", MAPPER.createObjectNode().put("pbadslot", "pbadslot"));
        ext.set("gpid", TextNode.valueOf("gpid"));

        final Imp imp = Imp.builder()
                .id("impId")
                .tagid("tagId")
                .ext(ext)
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "srid");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenAdUnitCodesDoesNotMatchConfiguredCodes() {
        // given
        final ObjectNode prebid = MAPPER.createObjectNode();
        prebid.set("storedrequest", MAPPER.createObjectNode().put("id", "srid"));
        final ObjectNode ext = MAPPER.createObjectNode();
        ext.set("prebid", prebid);
        ext.set("data", MAPPER.createObjectNode().put("pbadslot", "pbadslot"));
        ext.set("gpid", TextNode.valueOf("gpid"));

        final Imp imp = Imp.builder()
                .id("impId")
                .tagid("tagId")
                .ext(ext)
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "adUnitCode");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String... codes) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithCodes(codes),
                RequestRuleContext.of(AuctionContext.builder().build(), new Granularity.Imp("impId"), "datacenter"));
    }

    private ObjectNode givenConfigWithCodes(String... codes) {
        final ArrayNode codesNode = MAPPER.createArrayNode();
        Arrays.stream(codes).map(TextNode::valueOf).forEach(codesNode::add);
        return MAPPER.createObjectNode().set("codes", codesNode);
    }
}
