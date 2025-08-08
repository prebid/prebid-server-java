package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserFpdAvailableFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final UserFpdAvailableFunction target = new UserFpdAvailableFunction();

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
    public void extractShouldReturnTrueWhenNonNullUserDataIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().data(Collections.singletonList(Data.builder().build())).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnTrueWhenUserExtDataIsPresent() {
        // given
        final ObjectNode extUserData = mapper.createObjectNode().set("someData", TextNode.valueOf("someData"));
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().ext(ExtUser.builder().data(extUserData).build()).build())
                .build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("true");
    }

    @Test
    public void extractShouldReturnFalseWhenUserDataAndUserExtDataAreAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments = givenFunctionArguments(bidRequest);

        // when and then
        assertThat(target.extract(arguments)).isEqualTo("false");
    }

    private static SchemaFunctionArguments<BidRequest, RequestRuleContext> givenFunctionArguments(
            BidRequest bidRequest) {

        return SchemaFunctionArguments.of(
                bidRequest,
                null,
                RequestRuleContext.of(AuctionContext.builder().build(), Granularity.Request.instance(), "datacenter"));
    }
}
