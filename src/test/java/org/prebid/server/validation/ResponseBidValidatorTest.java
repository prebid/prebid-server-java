package org.prebid.server.validation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.prebid.server.settings.model.BidValidationEnforcement.enforce;
import static org.prebid.server.settings.model.BidValidationEnforcement.skip;
import static org.prebid.server.settings.model.BidValidationEnforcement.warn;

public class ResponseBidValidatorTest extends VertxTest {

    private static final String BIDDER_NAME = "bidder";
    private static final String ACCOUNT_ID = "account";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Metrics metrics;

    private ResponseBidValidator responseBidValidator;

    @Mock
    private BidderAliases bidderAliases;

    @Before
    public void setUp() {
        responseBidValidator = new ResponseBidValidator(enforce, enforce, metrics, jacksonMapper, true);

        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);
    }

    @Test
    public void validateShouldFailedIfBidderBidCurrencyIsIncorrect() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.banner, "invalid", identity()),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("BidResponse currency \"invalid\" is not valid");
    }

    @Test
    public void validateShouldFailIfMissingBid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                BidderBid.of(null, null, "USD"), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Empty bid object submitted");
    }

    @Test
    public void validateShouldFailIfBidHasNoId() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.id(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid missing required field 'id'");
    }

    @Test
    public void validateShouldFailIfBidHasNoImpId() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId1\" missing required field 'impid'");
    }

    @Test
    public void validateShouldSuccessForDealZeroPriceBid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(builder -> builder.price(BigDecimal.valueOf(0)).dealid("dealId")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailIfBidHasNoCrid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.crid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId1\" missing creative ID");
    }

    @Test
    public void validateShouldFailIfBannerBidHasNoWidthAndHeight() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers creative size "
                        + "validation for bid bidId1, account=account, referrer=unknown, max imp size='100x200', bid "
                        + "response size='nullxnull'");
    }

    @Test
    public void validateShouldFailIfBannerBidWidthIsGreaterThanImposedByImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(150)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers creative size"
                        + " validation for bid bidId1, account=account, referrer=unknown, max imp size='100x200',"
                        + " bid response size='150x150'");
    }

    @Test
    public void validateShouldFailIfBannerBidHeightIsGreaterThanImposedByImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(50).h(250)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers creative size"
                        + " validation for bid bidId1, account=account, referrer=unknown, max imp size='100x200',"
                        + " bid response size='50x250'");
    }

    @Test
    public void validateShouldReturnSuccessIfNonBannerBidHasAnySize() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldTolerateMissingImpExtBidderNode() {
        // when
        final BidRequest bidRequest = givenRequest(impBuilder -> impBuilder
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode())));

        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)),
                BIDDER_NAME,
                givenAuctionContext(bidRequest),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfBannerBidHasInvalidSizeButAccountDoesNotEnforceValidation() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(150)),
                BIDDER_NAME,
                givenAuctionContext(
                        givenAccount(builder -> builder.auction(AccountAuctionConfig.builder()
                                .bidValidations(AccountBidValidationConfig.of(skip))
                                .build()))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailIfBidHasNoCorrespondingImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid("nonExistentsImpid")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has no corresponding imp in request");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureMarkerInCreativeInSecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers secure creative "
                        + "validation for bid bidId1, account=account, referrer=unknown,"
                        + " adm=<tag>http://site.com/creative.jpg</tag>");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureEncodedMarkerInCreativeInSecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http%3A//site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers secure creative"
                        + " validation for bid bidId1, account=account, referrer=unknown, "
                        + "adm=<tag>http%3A//site.com/creative.jpg</tag>");
    }

    @Test
    public void validateShouldFailIfBidHasNoSecureMarkersInCreativeInSecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>//site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("BidResponse validation `enforce`: bidder `bidder` response triggers secure creative"
                        + " validation for bid bidId1, account=account, referrer=unknown, "
                        + "adm=<tag>//site.com/creative.jpg</tag>");
    }

    @Test
    public void validateShouldReturnSuccessIfBidHasInsecureCreativeInInsecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailedIfVideoBidHasNoNurlAndAdm() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.adm(null).nurl(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" with video type missing adm and nurl");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER_NAME, MetricName.badserverresponse);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidVideoBidWithNurl() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.adm(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidVideoBidWithAdm() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.nurl(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfBannerSizeValidationNotEnabled() {
        // given
        responseBidValidator = new ResponseBidValidator(skip, enforce, metrics, jacksonMapper, true);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessWithWarningIfBannerSizeEnforcementIsWarn() {
        // given
        responseBidValidator = new ResponseBidValidator(warn, enforce, metrics, jacksonMapper, true);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("BidResponse validation `warn`: bidder `bidder` response triggers creative size "
                        + "validation for bid bidId1, account=account, referrer=unknown, max imp size='100x200',"
                        + " bid response size='nullxnull'");
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        // given
        responseBidValidator = new ResponseBidValidator(enforce, skip, metrics, jacksonMapper, true);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessWithWarningIfSecureMarkupEnforcementIsWarn() {
        // given
        responseBidValidator = new ResponseBidValidator(enforce, warn, metrics, jacksonMapper, true);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("BidResponse validation `warn`: bidder `bidder` response triggers secure creative "
                        + "validation for bid bidId1, account=account, referrer=unknown, "
                        + "adm=<tag>http://site.com/creative.jpg</tag>");
    }

    @Test
    public void validateShouldIncrementSizeValidationErrMetrics() {
        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(200)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
    }

    @Test
    public void validateShouldIncrementSizeValidationWarnMetrics() {
        // given
        responseBidValidator = new ResponseBidValidator(warn, warn, metrics, jacksonMapper, true);

        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(200)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
    }

    @Test
    public void validateShouldIncrementSecureValidationErrMetrics() {
        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
    }

    @Test
    public void validateShouldIncrementSecureValidationWarnMetrics() {
        // given
        responseBidValidator = new ResponseBidValidator(warn, warn, metrics, jacksonMapper, true);

        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidNonDealBid() {
        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailIfBidHasNoDealid() {
        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(identity())),
                bidderAliases);

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" missing required field 'dealid'");
    }

    @Test
    public void validateShouldSuccessIfBidHasDealidAndImpHasNoDeals() {
        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(bid -> bid.dealid("dealId1")),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(identity())),
                bidderAliases);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    public void validateShouldWarnIfBidHasDealidMissingInImp() {
        given(bidderAliases.resolveBidder(eq("anotherBidder"))).willReturn("anotherBidder");

        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(bid -> bid.dealid("dealId1")),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp.pmp(pmp(asList(
                        deal(builder -> builder
                                .id("dealId2")
                                .ext(mapper.valueToTree(ExtDeal.of(
                                        ExtDealLine.of(null, null, null, BIDDER_NAME))))),
                        deal(builder -> builder
                                .id("dealId3")
                                .ext(mapper.valueToTree(ExtDeal.of(
                                        ExtDealLine.of(null, null, null, BIDDER_NAME))))),
                        deal(builder -> builder
                                .id("dealId4")
                                .ext(mapper.valueToTree(ExtDeal.of(
                                        ExtDealLine.of(null, null, null, "anotherBidder")))))))))),
                bidderAliases);

        assertThat(result.getWarnings()).hasSize(1)
                .containsOnly("WARNING: Bid \"bidId1\" has 'dealid' not present in corresponding imp in request."
                        + " 'dealid' in bid: 'dealId1', deal Ids in imp: 'dealId2,dealId3'");
    }

    @Test
    public void validateShouldFailIfBidIsBannerAndImpHasNoBanner() {
        responseBidValidator = new ResponseBidValidator(skip, enforce, metrics, jacksonMapper, true);

        final ValidationResult result = responseBidValidator.validate(
                givenBid(bid -> bid.dealid("dealId1")),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp
                        .pmp(pmp(singletonList(deal(builder -> builder.id("dealId1"))))))),
                bidderAliases);

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" has banner media type but corresponding imp in request is missing "
                        + "'banner' object");
    }

    @Test
    public void validateShouldFailIfBidIsBannerAndSizeHasNoMatchInBannerFormats() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(bid -> bid.dealid("dealId1").w(300).h(400)),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp
                        .pmp(pmp(singletonList(deal(builder -> builder.id("dealId1")))))
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(500).build()))
                                .build()))),
                bidderAliases);

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' not supported by corresponding imp in request. Bid "
                        + "dimensions: '300x400', formats in imp: '400x500'");
    }

    @Test
    public void validateShouldFailIfBidIsBannerAndSizeHasNoMatchInLineItem() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(bid -> bid.dealid("dealId1").w(300).h(400)),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp
                        .pmp(pmp(singletonList(deal(builder -> builder
                                .id("dealId1")
                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of("lineItemId", null,
                                        singletonList(Format.builder().w(500).h(600).build()), null))))))))
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(400).build()))
                                .build()))),
                bidderAliases);

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' not matched to Line Item. Bid dimensions: '300x400', "
                        + "Line Item sizes: '500x600'");
    }

    @Test
    public void validateShouldSuccessIfBidIsBannerAndSizeHasNoMatchInLineItemForNonPgDeal() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(bid -> bid.dealid("dealId1").w(300).h(400)),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp
                        .pmp(pmp(singletonList(deal(builder -> builder
                                .id("dealId1")
                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(null, null,
                                        singletonList(Format.builder().w(500).h(600).build()), null))))))))
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(400).build()))
                                .build()))),
                bidderAliases);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidDealNonBannerBid() {
        final ValidationResult result = responseBidValidator.validate(
                givenVideoBid(bid -> bid.dealid("dealId1")),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp.pmp(pmp(singletonList(
                        deal(builder -> builder.id("dealId1"))))))),
                bidderAliases);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidDealBannerBid() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(bid -> bid.dealid("dealId1").w(300).h(400)),
                BIDDER_NAME,
                givenAuctionContext(givenRequest(imp -> imp
                        .pmp(pmp(singletonList(deal(builder -> builder
                                .id("dealId1")
                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(null, null,
                                        singletonList(Format.builder().w(300).h(400).build()), null))))))))
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(400).build()))
                                .build()))),
                bidderAliases);

        assertThat(result.hasErrors()).isFalse();
    }

    private BidRequest givenRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final ObjectNode ext = mapper.createObjectNode().set(
                "prebid", mapper.createObjectNode().set(
                        "bidder", mapper.createObjectNode().set(
                                BIDDER_NAME, mapper.createObjectNode().put(
                                        "dealsonly", true))));

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id("impId1")
                .ext(ext);
        final Imp imp = impCustomizer.apply(impBuilder).build();

        return BidRequest.builder().imp(singletonList(imp)).build();
    }

    private static Pmp pmp(List<Deal> deals) {
        return Pmp.builder().deals(deals).build();
    }

    private static Deal deal(UnaryOperator<Deal.DealBuilder> dealCustomizer) {
        return dealCustomizer.apply(Deal.builder()).build();
    }

    private static BidderBid givenVideoBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBid(BidType.video, bidCustomizer);
    }

    private static BidderBid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBid(BidType.banner, bidCustomizer);
    }

    private static BidderBid givenBid(BidType type, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBid(type, "USD", bidCustomizer);
    }

    private static BidderBid givenBid(BidType type, String bidCurrency, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        final Bid.BidBuilder bidBuilder = Bid.builder()
                .id("bidId1")
                .adm("adm1")
                .nurl("nurl")
                .impid("impId1")
                .crid("crid1")
                .w(1)
                .h(1)
                .adm("<tag>https://site.com/creative.jpg</tag>")
                .price(BigDecimal.ONE);

        return BidderBid.of(bidCustomizer.apply(bidBuilder).build(), type, bidCurrency);
    }

    private static AuctionContext givenAuctionContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .account(account)
                .bidRequest(bidRequest)
                .build();
    }

    private static AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return givenAuctionContext(bidRequest, givenAccount());
    }

    private static AuctionContext givenAuctionContext(Account account) {
        return givenAuctionContext(givenBidRequest(identity()), account);
    }

    private static AuctionContext givenAuctionContext() {
        return givenAuctionContext(givenBidRequest(identity()), givenAccount());
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                        .format(asList(Format.builder().w(100).h(200).build(), Format.builder().w(50).h(50).build()))
                        .build())
                .ext(mapper.createObjectNode().set(
                        "prebid", mapper.createObjectNode().set(
                                "bidder", mapper.createObjectNode()
                                        .putNull(BIDDER_NAME))));

        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(impBuilder).build()))
                .build();
    }

    private static Account givenAccount() {
        return givenAccount(identity());
    }

    private static Account givenAccount(UnaryOperator<Account.AccountBuilder> accountCustomizer) {
        return accountCustomizer.apply(Account.builder().id(ACCOUNT_ID)).build();
    }
}
