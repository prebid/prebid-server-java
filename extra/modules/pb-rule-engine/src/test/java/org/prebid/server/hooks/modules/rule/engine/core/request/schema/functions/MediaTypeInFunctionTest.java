package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
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

public class MediaTypeInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final MediaTypeInFunction target = new MediaTypeInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("types", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenTypesFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode typesNode = mapper.createArrayNode();
        typesNode.add(TextNode.valueOf("test"));
        typesNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("types", typesNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'types' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenBannerPresentOnProvidedImpAndConfiguredTypes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .banner(Banner.builder().build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "impId", "banner");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenVideoPresentOnProvidedImpAndConfiguredTypes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .video(Video.builder().build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "impId", "video");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenAudioPresentOnProvidedImpAndConfiguredTypes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .audio(Audio.builder().build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "impId", "audio");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenNativePresentOnProvidedImpAndConfiguredTypes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .xNative(Native.builder().build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "impId", "native");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenImpMediaTypeIsAbsentInConfiguredTypes() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .xNative(Native.builder().build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Collections.singletonList(imp))
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "impId", "expectedMediaType");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String impId,
            String... types) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithTypes(types),
                RequestRuleContext.of(AuctionContext.builder().build(), new Granularity.Imp(impId), "datacenter"));
    }

    private ObjectNode givenConfigWithTypes(String... types) {
        final ArrayNode typesNode = mapper.createArrayNode();
        Arrays.stream(types).map(TextNode::valueOf).forEach(typesNode::add);
        return mapper.createObjectNode().set("types", typesNode);
    }
}
