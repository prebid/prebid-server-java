package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DomainInFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DomainInFunction target = new DomainInFunction();

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'domains' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(mapper.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'domains' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsNotAnArray() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("domains", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'domains' is required and has to be an array of strings");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenDomainsFieldIsNotAnArrayOfStrings() {
        // given
        final ArrayNode domainsNode = mapper.createArrayNode();
        domainsNode.add(TextNode.valueOf("test"));
        domainsNode.add(IntNode.valueOf(1));
        final ObjectNode config = mapper.createObjectNode().set("domains", domainsNode);

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'domains' is required and has to be an array of strings");
    }

    @Test
    public void extractShouldReturnTrueWhenSitePublisherDomainIsPresentInConfiguredDomains() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().domain("sitePubDomain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "sitePubDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenAppPublisherDomainIsPresentInConfiguredDomains() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().domain("sitePubDomain").build()).build())
                .app(App.builder().publisher(Publisher.builder().domain("appPubDomain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "appPubDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenDoohPublisherDomainIsPresentInConfiguredDomains() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().domain("sitePubDomain").build()).build())
                .app(App.builder().publisher(Publisher.builder().domain("appPubDomain").build()).build())
                .dooh(Dooh.builder().publisher(Publisher.builder().domain("doohPubDomain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "doohPubDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenSiteDomainIsPresentInConfiguredDomains() {
        // given
        final Site site = Site.builder()
                .publisher(Publisher.builder().domain("sitePubDomain").build())
                .domain("siteDomain")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .app(App.builder().publisher(Publisher.builder().domain("appPubDomain").build()).build())
                .dooh(Dooh.builder().publisher(Publisher.builder().domain("doohPubDomain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "siteDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenAppDomainIsPresentInConfiguredDomains() {
        // given
        final Site site = Site.builder()
                .publisher(Publisher.builder().domain("sitePubDomain").build())
                .domain("siteDomain")
                .build();

        final App app = App.builder()
                .publisher(Publisher.builder().domain("appPubDomain").build())
                .domain("appDomain")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .app(app)
                .dooh(Dooh.builder().publisher(Publisher.builder().domain("doohPubDomain").build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "appDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenDoohDomainIsPresentInConfiguredDomains() {
        // given
        final Site site = Site.builder()
                .publisher(Publisher.builder().domain("sitePubDomain").build())
                .domain("siteDomain")
                .build();

        final App app = App.builder()
                .publisher(Publisher.builder().domain("appPubDomain").build())
                .domain("appDomain")
                .build();

        final Dooh dooh = Dooh.builder()
                .publisher(Publisher.builder().domain("doohPubDomain").build())
                .domain("doohDomain")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .app(app)
                .dooh(dooh)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "doohDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenAllSuppliedDomainsAreAbsentInConfiguredDomains() {
        // given
        final Site site = Site.builder()
                .publisher(Publisher.builder().domain("sitePubDomain").build())
                .domain("siteDomain")
                .build();

        final App app = App.builder()
                .publisher(Publisher.builder().domain("appPubDomain").build())
                .domain("appDomain")
                .build();

        final Dooh dooh = Dooh.builder()
                .publisher(Publisher.builder().domain("doohPubDomain").build())
                .domain("doohDomain")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .app(app)
                .dooh(dooh)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments =
                givenFunctionArguments(bidRequest, "expectedDomain");

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest,
            String... domains) {

        return SchemaFunctionArguments.of(
                bidRequest,
                givenConfigWithDomains(domains),
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }

    private ObjectNode givenConfigWithDomains(String... domains) {
        final ArrayNode domainsNode = mapper.createArrayNode();
        Arrays.stream(domains).map(TextNode::valueOf).forEach(domainsNode::add);
        return mapper.createObjectNode().set("domains", domainsNode);
    }
}
