package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ArrayOverride;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attribute;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.AttributeActionOverrides;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Attributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.Conditions;
import org.prebid.server.hooks.modules.ortb2.blocking.core.config.ModuleConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;

import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class BlockedAttributesResolverTest {

    private static final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    public void shouldReturnEmptyResultWhenInvalidAccountConfigurationAndDebugDisabled() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode().put("block-lists", 1);
        final BlockedAttributesResolver resolver = BlockedAttributesResolver.create(
                emptyRequest(), "bidder1", accountConfig, false);

        // when and then
        assertThat(resolver.resolve()).isEqualTo(ExecutionResult.empty());
    }

    @Test
    public void shouldReturnResultWithErrorWhenInvalidAccountConfiguration() {
        // given
        final ObjectNode accountConfig = mapper.createObjectNode().put("attributes", 1);
        final BlockedAttributesResolver resolver = BlockedAttributesResolver.create(
                emptyRequest(), "bidder1", accountConfig, true);

        // when and then
        assertThat(resolver.resolve()).isEqualTo(
                ExecutionResult.withError("attributes field in account configuration is not an object"));
    }

    @Test
    public void shouldReturnResultWithValueAndWarnings() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .actionOverrides(AttributeActionOverrides.blocked(
                                asList(
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), singletonList("video")),
                                                singletonList("domain3.com")),
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), singletonList("banner")),
                                                singletonList("domain4.com")))))
                        .build())
                .build()));
        final BlockedAttributesResolver resolver = BlockedAttributesResolver.create(
                request(imp -> imp
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())),
                "bidder1",
                accountConfig,
                true);

        // when and then
        assertThat(resolver.resolve()).isEqualTo(ExecutionResult.builder()
                .value(BlockedAttributes.builder().badv(singletonList("domain3.com")).build())
                .warnings(singletonList("More than one conditions matches request. Bidder: bidder1, " +
                        "request media types: [banner, video]"))
                .build());
    }

    @Test
    public void shouldReturnResultWithValueAndWarningsWhenDebugDisabled() {
        // given
        final ObjectNode accountConfig = toObjectNode(ModuleConfig.of(Attributes.builder()
                .badv(Attribute.badvBuilder()
                        .actionOverrides(AttributeActionOverrides.blocked(
                                asList(
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), singletonList("video")),
                                                singletonList("domain3.com")),
                                        ArrayOverride.of(
                                                Conditions.of(singletonList("bidder1"), singletonList("banner")),
                                                singletonList("domain4.com")))))
                        .build())
                .build()));
        final BlockedAttributesResolver resolver = BlockedAttributesResolver.create(
                request(imp -> imp
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())),
                "bidder1",
                accountConfig,
                false);

        // when and then
        assertThat(resolver.resolve()).isEqualTo(ExecutionResult.builder()
                .value(BlockedAttributes.builder().badv(singletonList("domain3.com")).build())
                .build());
    }

    private static BidRequest emptyRequest() {
        return BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .build();
    }

    private static BidRequest request(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder()).build()))
                .build();
    }

    private static ObjectNode toObjectNode(ModuleConfig config) {
        return mapper.valueToTree(config);
    }
}
