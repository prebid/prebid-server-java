package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AdUnitCodeFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final AdUnitCodeFunction target = new AdUnitCodeFunction();

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
    public void extractShouldReturnGpidWhenPresent() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .ext(mapper.createObjectNode().put("gpid", "gpid"))
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, new Granularity.Imp("impId"), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("gpid");
    }

    @Test
    public void extractShouldReturnTagidWhenGpidAbsentAndTagidPresent() {
        // given
        final Imp imp = Imp.builder()
                .id("impId")
                .tagid("tagId")
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, new Granularity.Imp("impId"), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("tagId");
    }

    @Test
    public void extractShouldReturnPbAdSlotWhenGpidAndTagidAreAbsent() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("data", mapper.createObjectNode().put("pbadslot", "pbadslot"));

        final Imp imp = Imp.builder().id("impId").ext(ext).build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, new Granularity.Imp("impId"), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("pbadslot");
    }

    @Test
    public void extractShouldReturnStoredRequestIdWhenGpidAndTagidAndPbAdSlotAreAbsent() {
        // given
        final ObjectNode prebid = mapper.createObjectNode();
        prebid.set("storedrequest", mapper.createObjectNode().put("id", "srid"));
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", prebid);

        final Imp imp = Imp.builder().id("impId").ext(ext).build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, new Granularity.Imp("impId"), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("srid");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenAllAdUnitCodeSourcesAreAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, new Granularity.Imp("impId"), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("undefined");
    }
}
