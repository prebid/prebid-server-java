package org.prebid.server.validation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.RejectedBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_GENERAL;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED;
import static org.prebid.server.settings.model.BidValidationEnforcement.enforce;
import static org.prebid.server.settings.model.BidValidationEnforcement.skip;
import static org.prebid.server.settings.model.BidValidationEnforcement.warn;

@ExtendWith(MockitoExtension.class)
public class ResponseBidValidatorTest extends VertxTest {

    private static final String BIDDER_NAME = "bidder";
    private static final String ACCOUNT_ID = "account";

    @Mock
    private Metrics metrics;

    @Mock
    private BidRejectionTracker bidRejectionTracker;

    private ResponseBidValidator target;

    @Mock(strictness = LENIENT)
    private BidderAliases bidderAliases;

    @BeforeEach
    public void setUp() {
        target = new ResponseBidValidator(enforce, enforce, metrics, 0.01);

        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);
        given(bidderAliases.isAllowedAlternateBidderCode(anyString(), anyString())).willReturn(true);
    }

    @Test
    public void validateShouldFailedIfBidderBidCurrencyIsIncorrect() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.banner, "invalid", identity()),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("BidResponse currency \"invalid\" is not valid");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfMissingBid() {
        // when
        final ValidationResult result = target.validate(
                BidderBid.of(null, null, "USD"),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Empty bid object submitted");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoId() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.id(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid missing required field 'id'");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoImpId() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.impid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId1\" missing required field 'impid'");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldSuccessForDealZeroPriceBid() {
        // when
        final ValidationResult result = target.validate(
                givenVideoBid(builder -> builder.price(BigDecimal.valueOf(0)).dealid("dealId")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoCrid() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.crid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId1\" missing creative ID");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBannerBidHasNoWidthAndHeight() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(null).h(null));
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId1, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='nullxnull'""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED));
    }

    @Test
    public void validateShouldFailIfBannerBidWidthIsGreaterThanImposedByImp() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(150).h(150));
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId1, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='150x150'""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED));
    }

    @Test
    public void validateShouldFailIfBannerBidHeightIsGreaterThanImposedByImp() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(50).h(250));
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId1, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='50x250'""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED));
    }

    @Test
    public void validateShouldReturnSuccessIfNonBannerBidHasAnySize() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldTolerateMissingImpExtBidderNode() {
        // when
        final BidRequest bidRequest = givenRequest(impBuilder -> impBuilder
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode())));

        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)),
                BIDDER_NAME,
                givenAuctionContext(bidRequest),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessIfBannerBidHasInvalidSizeButAccountDoesNotEnforceValidation() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.w(150).h(150)),
                BIDDER_NAME,
                givenAuctionContext(
                        givenAccount(builder -> builder.auction(AccountAuctionConfig.builder()
                                .bidValidations(AccountBidValidationConfig.of(skip))
                                .build()))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoCorrespondingImp() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.impid("nonExistentsImpid")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has no corresponding imp in request");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasInsecureMarkerInCreativeInSecureContext() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId1, account=account, referrer=unknown, \
                        adm=<tag>http://site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE));
    }

    @Test
    public void validateShouldFailIfBidHasInsecureEncodedMarkerInCreativeInSecureContext() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>http%3A//site.com/creative.jpg</tag>"));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId1, account=account, referrer=unknown, \
                        adm=<tag>http%3A//site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE));
    }

    @Test
    public void validateShouldFailIfBidHasNoSecureMarkersInCreativeInSecureContext() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>//site.com/creative.jpg</tag>"));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId1, account=account, referrer=unknown, \
                        adm=<tag>//site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE));
    }

    @Test
    public void validateShouldReturnSuccessIfBidHasInsecureCreativeInInsecureContext() {
        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailedIfVideoBidHasNoNurlAndAdm() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.adm(null).nurl(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" with video type missing adm and nurl");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER_NAME, MetricName.badserverresponse);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidVideoBidWithNurl() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.adm(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidVideoBidWithAdm() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.nurl(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        // when
        final ValidationResult result = target.validate(
                givenBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessIfBannerSizeValidationNotEnabled() {
        // given
        target = new ResponseBidValidator(skip, enforce, metrics, 0.01);

        // when
        final ValidationResult result = target.validate(
                givenBid(identity()),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessWithWarningIfBannerSizeEnforcementIsWarn() {
        // given
        target = new ResponseBidValidator(warn, enforce, metrics, 0.01);

        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(null).h(null));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("""
                        BidResponse validation `warn`: bidder `bidder` response triggers \
                        creative size validation for bid bidId1, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='nullxnull'""");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        // given
        target = new ResponseBidValidator(enforce, skip, metrics, 0.01);

        // when
        final ValidationResult result = target.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessWithWarningIfSecureMarkupEnforcementIsWarn() {
        // given
        target = new ResponseBidValidator(enforce, warn, metrics, 0.01);

        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("""
                        BidResponse validation `warn`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId1, account=account, referrer=unknown, \
                        adm=<tag>http://site.com/creative.jpg</tag>""");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldIncrementSizeValidationErrMetrics() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(150).h(200));
        target.validate(givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED));
    }

    @Test
    public void validateShouldIncrementSizeValidationWarnMetrics() {
        // given
        target = new ResponseBidValidator(warn, warn, metrics, 0.01);

        // when
        final BidderBid givenBid = givenBid(builder -> builder.w(150).h(200));
        target.validate(givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldIncrementSecureValidationErrMetrics() {
        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));
        target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker)
                .reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE));
    }

    @Test
    public void validateShouldIncrementSecureValidationWarnMetrics() {
        // given
        target = new ResponseBidValidator(warn, warn, metrics, 0.01);

        // when
        final BidderBid givenBid = givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));
        target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldNotFailOnSeatValidationWhenSeatEqualsIgnoringCaseToBidder() {
        // when
        final ValidationResult result = target.validate(
                givenBid(identity()).toBuilder().seat("biDDEr").build(),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        verify(metrics, never()).updateSeatValidationMetrics(BIDDER_NAME);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailOnSeatValidationWhenSeatIsNotAllowed() {
        // given
        final BidderBid givenBid = givenBid(identity()).toBuilder().seat("seat").build();
        given(bidderAliases.isAllowedAlternateBidderCode(BIDDER_NAME, "seat")).willReturn(false);

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenAccount(identity())),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("invalid bidder code seat was set by the adapter bidder for the account account");
        verify(metrics).updateSeatValidationMetrics(BIDDER_NAME);
        verify(bidRejectionTracker).reject(RejectedBid.of(givenBid, RESPONSE_REJECTED_GENERAL));
    }

    @Test
    public void validateShouldNotFailOnSeatValidationWhenSeatIsAllowed() {
        // given
        final BidderBid givenBid = givenBid(identity()).toBuilder().seat("seat").build();
        given(bidderAliases.isAllowedAlternateBidderCode(BIDDER_NAME, "seat")).willReturn(true);

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenAccount(identity())),
                bidderAliases);

        // then
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        verify(metrics, never()).updateSeatValidationMetrics(BIDDER_NAME);
        verifyNoInteractions(bidRejectionTracker);
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

    private AuctionContext givenAuctionContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .bidRejectionTrackers(Map.of("bidder", bidRejectionTracker))
                .account(account)
                .bidRequest(bidRequest)
                .build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return givenAuctionContext(bidRequest, givenAccount());
    }

    private AuctionContext givenAuctionContext(Account account) {
        return givenAuctionContext(givenBidRequest(identity()), account);
    }

    private AuctionContext givenAuctionContext() {
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
