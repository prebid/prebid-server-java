package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DomainFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DomainFunction target = new DomainFunction();

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
    public void extractShouldReturnSitePublisherDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().domain("domain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldReturnAppPublisherDomainWhenSiteHasNoDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().publisher(Publisher.builder().domain("domain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldReturnDoohPublisherDomainWhenSiteAndAppHaveNoDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .dooh(Dooh.builder().publisher(Publisher.builder().domain("domain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldReturnSiteDomainWhenPublisherHasNoDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("domain").build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldReturnAppDomainWhenPublisherAndSiteHaveNoDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().domain("domain").build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldReturnDoohDomainWhenPublisherAndSiteAndAppHaveNoDomain() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .dooh(Dooh.builder().domain("domain").build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("domain");
    }

    @Test
    public void extractShouldFallbackToUndefinedWhenDomainIsAbsent() {
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
