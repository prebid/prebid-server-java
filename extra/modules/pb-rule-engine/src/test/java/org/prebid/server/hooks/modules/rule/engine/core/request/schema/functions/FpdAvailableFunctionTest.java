package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FpdAvailableFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FpdAvailableFunction target = new FpdAvailableFunction();

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
    public void extractShouldReturnTrueWhenUserDataPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().data(Collections.singletonList(Data.builder().build())).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenUserExtDataPresent() {
        // given
        final ObjectNode extData = MAPPER.createObjectNode().set("someData", TextNode.valueOf("someData"));
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().ext(ExtUser.builder().data(extData).build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenSiteContentDataPresent() {
        // given
        final Site site = Site.builder()
                .content(Content.builder().data(Collections.singletonList(Data.builder().build())).build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenSiteExtDataPresent() {
        // given
        final ObjectNode extData = MAPPER.createObjectNode().set("someData", TextNode.valueOf("someData"));
        final Site site = Site.builder()
                .ext(ExtSite.of(null, extData))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenAppContentDataPresent() {
        // given
        final App app = App.builder()
                .content(Content.builder().data(Collections.singletonList(Data.builder().build())).build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .app(app)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenAppExtDataPresent() {
        // given
        final ObjectNode extData = MAPPER.createObjectNode().set("someData", TextNode.valueOf("someData"));
        final App app = App.builder()
                .ext(ExtApp.of(null, extData))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .app(app)
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenNoFpdAvailable() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest) {

        return SchemaFunctionArguments.of(
                bidRequest,
                null,
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }
}
