package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleAction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogATagFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LogATagFunction target = new LogATagFunction(MAPPER);

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'analyticsValue' is required and has to be a string");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenAnalyticsValueFieldIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(MAPPER.createObjectNode()))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'analyticsValue' is required and has to be a string");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenAnalyticsValueFieldIsNotAString() {
        // given
        final ObjectNode config = MAPPER.createObjectNode().set("analyticsValue", MAPPER.createObjectNode());

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Field 'analyticsValue' is required and has to be a string");
    }

    @Test
    public void applyShouldEmitATagForRequestAndNotModifyOperand() {
        // given
        final RequestRuleContext context = RequestRuleContext.of(
                AuctionContext.builder().build(), Granularity.Request.instance(), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                InfrastructureArguments.<RequestRuleContext>builder()
                        .context(context)
                        .schemaFunctionResults(Collections.emptyMap())
                        .schemaFunctionMatches(Collections.emptyMap())
                        .ruleFired("ruleFired")
                        .analyticsKey("analyticsKey")
                        .modelVersion("modelVersion")
                        .build();

        final ObjectNode config = MAPPER.createObjectNode()
                .set("analyticsValue", TextNode.valueOf("analyticsValue"));

        final ResultFunctionArguments<BidRequest, RequestRuleContext> resultFunctionArguments =
                ResultFunctionArguments.of(BidRequest.builder().build(), config, infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(resultFunctionArguments);

        // then
        final ObjectNode expectedValues = MAPPER.createObjectNode();
        expectedValues.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedValues.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedValues.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedValues.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedValues.set("resultFunction", TextNode.valueOf("logAtag"));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder().impIds(Collections.singletonList("*")).build();
        final Result expectedResult = ResultImpl.of("success", expectedValues, expectedAppliedTo);
        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine", "success", Collections.singletonList(expectedResult));
        final Tags expectedTags = TagsImpl.of(Collections.singletonList(expectedActivity));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        BidRequest.builder().build(),
                        RuleAction.NO_ACTION,
                        expectedTags,
                        Collections.emptyList()));
    }

    @Test
    public void applyShouldEmitATagForImpAndNotModifyOperand() {
        // given
        final RequestRuleContext context = RequestRuleContext.of(
                AuctionContext.builder().build(), new Granularity.Imp("impId"), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                InfrastructureArguments.<RequestRuleContext>builder()
                        .context(context)
                        .schemaFunctionResults(Collections.emptyMap())
                        .schemaFunctionMatches(Collections.emptyMap())
                        .ruleFired("ruleFired")
                        .analyticsKey("analyticsKey")
                        .modelVersion("modelVersion")
                        .build();

        final ObjectNode config = MAPPER.createObjectNode()
                .set("analyticsValue", TextNode.valueOf("analyticsValue"));

        final ResultFunctionArguments<BidRequest, RequestRuleContext> resultFunctionArguments =
                ResultFunctionArguments.of(BidRequest.builder().build(), config, infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(resultFunctionArguments);

        // then
        final ObjectNode expectedValues = MAPPER.createObjectNode();
        expectedValues.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedValues.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedValues.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedValues.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedValues.set("resultFunction", TextNode.valueOf("logAtag"));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder().impIds(Collections.singletonList("impId")).build();
        final Result expectedResult = ResultImpl.of("success", expectedValues, expectedAppliedTo);
        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine", "success", Collections.singletonList(expectedResult));
        final Tags expectedTags = TagsImpl.of(Collections.singletonList(expectedActivity));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        BidRequest.builder().build(),
                        RuleAction.NO_ACTION,
                        expectedTags,
                        Collections.emptyList()));
    }
}
