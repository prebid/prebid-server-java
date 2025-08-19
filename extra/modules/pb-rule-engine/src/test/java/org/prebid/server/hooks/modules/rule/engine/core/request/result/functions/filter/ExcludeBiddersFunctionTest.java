package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.proto.Uids;
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
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
class ExcludeBiddersFunctionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ExcludeBiddersFunction target;

    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;

    @BeforeEach
    void setUp() {
        target = new ExcludeBiddersFunction(mapper, bidderCatalog);
    }

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsAbsent() {
        // when and then
        assertThatThrownBy(() -> target.validateConfig(null))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("Configuration is required, but not provided");
    }

    @Test
    public void validateConfigShouldThrowErrorWhenConfigIsInvalid() {
        // given
        final ObjectNode config = mapper.createObjectNode().set("bidders", TextNode.valueOf("test"));

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class);
    }

    @Test
    public void validateConfigShouldThrowErrorWhenBiddersFieldIsEmpty() {
        // given
        final ObjectNode config = mapper.createObjectNode();

        // when and then
        assertThatThrownBy(() -> target.validateConfig(config))
                .isInstanceOf(ConfigurationValidationException.class)
                .hasMessage("'bidders' field is required");
    }

    @Test
    void applyShouldExcludeBiddersSpecifiedInConfigAndEmitSeatNonBidsWithATags() {
        // given 
        final BidRequest bidRequest = givenBidRequest(givenImp("impId", "bidder1", "bidder2"));

        final RequestRuleContext context = RequestRuleContext.of(
                givenAuctionContext(), Granularity.Request.instance(), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                givenInfrastructureArguments(context);

        final FilterBiddersFunctionConfig config = FilterBiddersFunctionConfig.builder()
                .bidders(Collections.singleton("bidder1"))
                .seatNonBid(BidRejectionReason.REQUEST_BLOCKED_GENERAL)
                .analyticsValue("analyticsValue")
                .build();

        final ResultFunctionArguments<BidRequest, RequestRuleContext> arguments =
                ResultFunctionArguments.of(bidRequest, mapper.valueToTree(config), infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(arguments);

        // then
        final ObjectNode expectedResultValue = mapper.createObjectNode();
        expectedResultValue.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedResultValue.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedResultValue.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedResultValue.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedResultValue.set("resultFunction", TextNode.valueOf("excludeBidders"));
        expectedResultValue.set("biddersRemoved", mapper.createArrayNode().add("bidder1"));
        expectedResultValue.set("seatNonBid", IntNode.valueOf(BidRejectionReason.REQUEST_BLOCKED_GENERAL.getValue()));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder().impIds(Collections.singletonList("impId")).build();
        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine",
                "success",
                Collections.singletonList(ResultImpl.of("success", expectedResultValue, expectedAppliedTo)));

        final SeatNonBid expectedSeatNonBid = SeatNonBid.of(
                "bidder1",
                Collections.singletonList(NonBid.of("impId", BidRejectionReason.REQUEST_BLOCKED_GENERAL)));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        givenBidRequest(givenImp("impId", "bidder2")),
                        RuleAction.UPDATE,
                        givenATags(expectedActivity),
                        Collections.singletonList(expectedSeatNonBid)));
    }

    @Test
    void applyShouldExcludeBiddersSpecifiedInConfigOnlyForSpecifiedImpWhenGranularityIsImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("impId", "bidder1", "bidder2"),
                givenImp("impId2", "bidder3", "bidder4"));

        final RequestRuleContext context = RequestRuleContext.of(
                givenAuctionContext(), new Granularity.Imp("impId2"), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                givenInfrastructureArguments(context);

        final FilterBiddersFunctionConfig config = FilterBiddersFunctionConfig.builder()
                .bidders(Collections.singleton("bidder3"))
                .seatNonBid(BidRejectionReason.REQUEST_BLOCKED_GENERAL)
                .analyticsValue("analyticsValue")
                .build();

        final ResultFunctionArguments<BidRequest, RequestRuleContext> arguments =
                ResultFunctionArguments.of(bidRequest, mapper.valueToTree(config), infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(arguments);

        // then
        final ObjectNode expectedResultValue = mapper.createObjectNode();
        expectedResultValue.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedResultValue.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedResultValue.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedResultValue.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedResultValue.set("resultFunction", TextNode.valueOf("excludeBidders"));
        expectedResultValue.set("biddersRemoved", mapper.createArrayNode().add("bidder3"));
        expectedResultValue.set("seatNonBid", IntNode.valueOf(BidRejectionReason.REQUEST_BLOCKED_GENERAL.getValue()));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder()
                .impIds(Collections.singletonList("impId2"))
                .build();

        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine",
                "success",
                Collections.singletonList(ResultImpl.of("success", expectedResultValue, expectedAppliedTo)));

        final SeatNonBid expectedSeatNonBid = SeatNonBid.of(
                "bidder3",
                Collections.singletonList(NonBid.of("impId2", BidRejectionReason.REQUEST_BLOCKED_GENERAL)));

        final BidRequest expectedBidRequest = givenBidRequest(
                givenImp("impId", "bidder1", "bidder2"),
                givenImp("impId2", "bidder4"));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        expectedBidRequest,
                        RuleAction.UPDATE,
                        givenATags(expectedActivity),
                        Collections.singletonList(expectedSeatNonBid)));
    }

    @Test
    void applyShouldDiscardImpIfAfterUpdateImpExtHasNoBidders() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("impId", "bidder1", "bidder2"),
                givenImp("impId2", "bidder3", "bidder4"));

        final RequestRuleContext context = RequestRuleContext.of(
                givenAuctionContext(), new Granularity.Imp("impId2"), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                givenInfrastructureArguments(context);

        final FilterBiddersFunctionConfig config = FilterBiddersFunctionConfig.builder()
                .bidders(Set.of("bidder3", "bidder4"))
                .seatNonBid(BidRejectionReason.REQUEST_BLOCKED_GENERAL)
                .analyticsValue("analyticsValue")
                .build();

        final ResultFunctionArguments<BidRequest, RequestRuleContext> arguments =
                ResultFunctionArguments.of(bidRequest, mapper.valueToTree(config), infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(arguments);

        // then
        final ObjectNode expectedResultValue = mapper.createObjectNode();
        expectedResultValue.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedResultValue.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedResultValue.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedResultValue.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedResultValue.set("resultFunction", TextNode.valueOf("excludeBidders"));
        expectedResultValue.set("biddersRemoved", mapper.createArrayNode().add("bidder3").add("bidder4"));
        expectedResultValue.set("seatNonBid", IntNode.valueOf(BidRejectionReason.REQUEST_BLOCKED_GENERAL.getValue()));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder()
                .impIds(Collections.singletonList("impId2"))
                .build();

        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine",
                "success",
                Collections.singletonList(ResultImpl.of("success", expectedResultValue, expectedAppliedTo)));


        final List<SeatNonBid> expectedSeatNonBid = List.of(
                SeatNonBid.of(
                        "bidder3",
                        Collections.singletonList(NonBid.of("impId2", BidRejectionReason.REQUEST_BLOCKED_GENERAL))),
                SeatNonBid.of(
                        "bidder4",
                        Collections.singletonList(NonBid.of("impId2", BidRejectionReason.REQUEST_BLOCKED_GENERAL))));

        final BidRequest expectedBidRequest = givenBidRequest(
                givenImp("impId", "bidder1", "bidder2"));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        expectedBidRequest,
                        RuleAction.UPDATE,
                        givenATags(expectedActivity),
                        expectedSeatNonBid));
    }

    @Test
    void applyShouldRejectBidRequestIfUpdatedRequestHasNoImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("impId", "bidder"));

        final RequestRuleContext context = RequestRuleContext.of(
                givenAuctionContext(), new Granularity.Imp("impId"), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                givenInfrastructureArguments(context);

        final FilterBiddersFunctionConfig config = FilterBiddersFunctionConfig.builder()
                .bidders(Set.of("bidder"))
                .seatNonBid(BidRejectionReason.REQUEST_BLOCKED_GENERAL)
                .analyticsValue("analyticsValue")
                .build();

        final ResultFunctionArguments<BidRequest, RequestRuleContext> arguments =
                ResultFunctionArguments.of(bidRequest, mapper.valueToTree(config), infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(arguments);

        // then
        final ObjectNode expectedResultValue = mapper.createObjectNode();
        expectedResultValue.set("analyticsKey", TextNode.valueOf("analyticsKey"));
        expectedResultValue.set("analyticsValue", TextNode.valueOf("analyticsValue"));
        expectedResultValue.set("modelVersion", TextNode.valueOf("modelVersion"));
        expectedResultValue.set("conditionFired", TextNode.valueOf("ruleFired"));
        expectedResultValue.set("resultFunction", TextNode.valueOf("excludeBidders"));
        expectedResultValue.set("biddersRemoved", mapper.createArrayNode().add("bidder"));
        expectedResultValue.set("seatNonBid", IntNode.valueOf(BidRejectionReason.REQUEST_BLOCKED_GENERAL.getValue()));

        final AppliedTo expectedAppliedTo = AppliedToImpl.builder()
                .impIds(Collections.singletonList("impId"))
                .build();

        final Activity expectedActivity = ActivityImpl.of(
                "pb-rule-engine",
                "success",
                Collections.singletonList(ResultImpl.of("success", expectedResultValue, expectedAppliedTo)));


        final List<SeatNonBid> expectedSeatNonBid = List.of(
                SeatNonBid.of(
                        "bidder",
                        Collections.singletonList(NonBid.of("impId", BidRejectionReason.REQUEST_BLOCKED_GENERAL))));

        assertThat(result).isEqualTo(RuleResult.rejected(givenATags(expectedActivity), expectedSeatNonBid));
    }

    @Test
    void applyShouldNotGenerateATagWhenNoAnalyticsKeySpecified() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("impId", "bidder1", "bidder2"));

        final RequestRuleContext context = RequestRuleContext.of(
                givenAuctionContext(), new Granularity.Imp("impId"), null);

        final InfrastructureArguments<RequestRuleContext> infrastructureArguments =
                InfrastructureArguments.<RequestRuleContext>builder()
                        .context(context)
                        .schemaFunctionResults(Collections.emptyMap())
                        .schemaFunctionMatches(Collections.emptyMap())
                        .ruleFired("ruleFired")
                        .modelVersion("modelVersion")
                        .build();

        final FilterBiddersFunctionConfig config = FilterBiddersFunctionConfig.builder()
                .bidders(Set.of("bidder1"))
                .seatNonBid(BidRejectionReason.REQUEST_BLOCKED_GENERAL)
                .analyticsValue("analyticsValue")
                .build();

        final ResultFunctionArguments<BidRequest, RequestRuleContext> arguments =
                ResultFunctionArguments.of(bidRequest, mapper.valueToTree(config), infrastructureArguments);

        // when
        final RuleResult<BidRequest> result = target.apply(arguments);

        // then
        final List<SeatNonBid> expectedSeatNonBid = List.of(
                SeatNonBid.of(
                        "bidder1",
                        Collections.singletonList(NonBid.of("impId", BidRejectionReason.REQUEST_BLOCKED_GENERAL))));

        assertThat(result).isEqualTo(
                RuleResult.of(
                        givenBidRequest(givenImp("impId", "bidder2")),
                        RuleAction.UPDATE,
                        givenATags(),
                        expectedSeatNonBid));
    }

    private static InfrastructureArguments<RequestRuleContext> givenInfrastructureArguments(
            RequestRuleContext context) {

        return InfrastructureArguments.<RequestRuleContext>builder()
                .context(context)
                .schemaFunctionResults(Collections.emptyMap())
                .schemaFunctionMatches(Collections.emptyMap())
                .ruleFired("ruleFired")
                .analyticsKey("analyticsKey")
                .modelVersion("modelVersion")
                .build();
    }

    private static Tags givenATags(Activity... activities) {
        return TagsImpl.of(Arrays.asList(activities));
    }

    private static AuctionContext givenAuctionContext(String... liveUidBidders) {
        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(new HashMap<>()).build(), new JacksonMapper(mapper));

        Arrays.stream(liveUidBidders).forEach(bidder -> uidsCookie.updateUid(bidder, "uid"));

        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().imp(Arrays.asList(imps)).build();
    }

    private static Imp givenImp(String impId, String... bidders) {
        return Imp.builder().id(impId).ext(givenImpExt(bidders)).build();
    }

    private static ObjectNode givenImpExt(String... bidders) {
        final ObjectNode biddersNode = mapper.createObjectNode();
        final ObjectNode dummyBidderConfigNode = mapper.createObjectNode().set("config", TextNode.valueOf("test"));
        Arrays.stream(bidders).forEach(bidder -> biddersNode.set(bidder, dummyBidderConfigNode));

        return mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().set("bidder", biddersNode));
    }
}
