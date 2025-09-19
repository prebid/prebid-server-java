package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
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

public class EidInFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EidInFunction target = new EidInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sources' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sources' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsNotAnArray() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("sources", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sources' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode sourcesNode = MAPPER.createArrayNode();
        sourcesNode.add(TextNode.valueOf("test"));
        sourcesNode.add(IntNode.valueOf(1));
        final ObjectNode config = MAPPER.createObjectNode().set("sources", sourcesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'sources' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenAnyOfUserEidSourcesPresentInConfiguredSources() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(Collections.singletonList(Eid.builder().source("source").build())).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "source");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenAllUserEidSourcesAbsentInConfiguredSources() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(Collections.singletonList(Eid.builder().source("source").build())).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "expectedSource");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String... sources) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithSources(sources),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }

    private ObjectNode givenConfigWithSources(String... sources) {
        final ArrayNode sourcesNode = MAPPER.createArrayNode();
        Arrays.stream(sources).map(TextNode::valueOf).forEach(sourcesNode::add);
        return MAPPER.createObjectNode().set("sources", sourcesNode);
    }
}
