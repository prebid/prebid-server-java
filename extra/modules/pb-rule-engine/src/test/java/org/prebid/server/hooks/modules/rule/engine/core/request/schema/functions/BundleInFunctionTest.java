package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BundleInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final BundleInFunction target = new BundleInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'bundles' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenBundlesFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'bundles' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenBundlesFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("bundles", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'bundles' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenBundlesFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode bundlesNode = mapper.createArrayNode();
        bundlesNode.add(TextNode.valueOf("test"));
        bundlesNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("bundles", bundlesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'bundles' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenBundlePresentInConfiguredBundles() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().bundle("bundle").build())
                .build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                givenConfigWithBundles("bundle"));

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenBundleIsAbsentInConfiguredBundles() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().bundle("bundle").build())
                .build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                givenConfigWithBundles("expectedBundle"));

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private ObjectNode givenConfigWithBundles(String... bundles) {
        final ArrayNode bundlesNode = mapper.createArrayNode();
        Arrays.stream(bundles).map(TextNode::valueOf).forEach(bundlesNode::add);
        return mapper.createObjectNode().set("bundles", bundlesNode);
    }
}
