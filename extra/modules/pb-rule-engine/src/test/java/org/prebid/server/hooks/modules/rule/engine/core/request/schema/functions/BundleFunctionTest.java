package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BundleFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final BundleFunction target = new BundleFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenArgumentsArePresent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("No arguments allowed");
    }

    @Test
    public void extractShouldReturnBundle() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().bundle("bundle").build())
                .build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("bundle");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenBundleIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<RequestSchemaContext> arguments = SchemaFunctionArguments.of(
                RequestSchemaContext.of(bidRequest, Granularity.Request.instance(), "datacenter"),
                null);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("undefined");
    }
}
