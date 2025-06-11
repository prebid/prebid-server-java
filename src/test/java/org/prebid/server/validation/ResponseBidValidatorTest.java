package org.prebid.server.validation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.settings.model.BidValidationEnforcement.enforce;
import static org.prebid.server.settings.model.BidValidationEnforcement.skip;
import static org.prebid.server.settings.model.BidValidationEnforcement.warn;

@ExtendWith(MockitoExtension.class)
public class ResponseBidValidatorTest extends VertxTest {

    private static final String BIDDER_NAME = "bidder";
    private static final String ACCOUNT_ID = "account";

    @Mock
    private Metrics metrics;
    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;
    @Mock
    private BidRejectionTracker bidRejectionTracker;

    private ResponseBidValidator target;

    @Mock(strictness = LENIENT)
    private BidderAliases bidderAliases;

    @BeforeEach
    public void setUp() {
        target = new ResponseBidValidator(
                enforce,
                enforce,
                enforce,
                currencyConversionService,
                metrics,
                jacksonMapper,
                0.01);

        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);
        given(bidderAliases.isAllowedAlternateBidderCode(anyString(), anyString())).willReturn(true);

        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void validateShouldFailedIfBidderBidCurrencyIsIncorrect() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, "invalid", identity()),
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
                givenBid(banner, builder -> builder.id(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid missing required field 'id'");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoImpId() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, builder -> builder.impid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId\" missing required field 'impid'");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldSuccessForDealZeroPriceBid() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.price(BigDecimal.valueOf(0)).dealid("dealId")),
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity())),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasNoCrid() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, builder -> builder.crid(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId\" missing creative ID");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBannerBidHasNoWidthAndHeight() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(null).h(null));

        // when
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='nullxnull'""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED);
    }

    @Test
    public void validateShouldFailIfBannerBidWidthIsGreaterThanImposedByImp() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(150).h(150));

        // when
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='150x150'""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED);
    }

    @Test
    public void validateShouldFailIfBannerBidHeightIsGreaterThanImposedByImp() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(50).h(250));

        // when
        final ValidationResult result = target.validate(
                givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        creative size validation for bid bidId, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='50x250'""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED);
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
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode())));

        // when
        final ValidationResult result = target.validate(
                givenBid(banner, builder -> builder.w(3).h(3)),
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
                givenBid(banner, builder -> builder.w(150).h(150)),
                BIDDER_NAME,
                givenAuctionContext(
                        givenAccount(builder -> builder.auction(AccountAuctionConfig.builder()
                                .bidValidations(AccountBidValidationConfig.of(skip, enforce))
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
                givenBid(banner, builder -> builder.impid("nonExistentsImpid")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId\" has no corresponding imp in request");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailIfBidHasInsecureMarkerInCreativeInSecureContext() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId, account=account, referrer=unknown, \
                        adm=<tag>http://site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE);
    }

    @Test
    public void validateShouldFailIfBidHasInsecureEncodedMarkerInCreativeInSecureContext() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>http%3A//site.com/creative.jpg</tag>"));

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId, account=account, referrer=unknown, \
                        adm=<tag>http%3A//site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE);
    }

    @Test
    public void validateShouldFailIfBidHasNoSecureMarkersInCreativeInSecureContext() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>//site.com/creative.jpg</tag>"));

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("""
                        BidResponse validation `enforce`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId, account=account, referrer=unknown, \
                        adm=<tag>//site.com/creative.jpg</tag>""");
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE);
    }

    @Test
    public void validateShouldReturnSuccessIfBidHasInsecureCreativeInInsecureContext() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
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
                givenAuctionContext(givenVideoImp(identity(), identity())),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Bid \"bidId\" with video type missing adm and nurl");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER_NAME, MetricName.badserverresponse);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidVideoBidWithNurl() {
        // when
        final ValidationResult result = target.validate(
                givenBid(BidType.video, builder -> builder.adm(null)),
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity())),
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
                givenAuctionContext(givenVideoImp(identity(), identity())),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, identity()),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessIfBannerSizeValidationNotEnabled() {
        // given
        target = new ResponseBidValidator(
                skip, enforce, enforce,
                currencyConversionService, metrics, jacksonMapper, 0.01);

        // when
        final ValidationResult result = target.validate(
                givenBid(banner, identity()),
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
        target = new ResponseBidValidator(
                warn, enforce, enforce,
                currencyConversionService, metrics, jacksonMapper, 0.01);
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(null).h(null));

        // when
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).containsOnly("""
                        BidResponse validation `warn`: bidder `bidder` response triggers \
                        creative size validation for bid bidId, account=account, referrer=unknown, \
                        max imp size='100x200', bid response size='nullxnull'""");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        // given
        target = new ResponseBidValidator(
                enforce, skip, enforce,
                currencyConversionService, metrics, jacksonMapper, 0.01);

        // when
        final ValidationResult result = target.validate(
                givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldReturnSuccessWithWarningIfSecureMarkupEnforcementIsWarn() {
        // given
        target = new ResponseBidValidator(
                enforce, warn, enforce,
                currencyConversionService, metrics, jacksonMapper, 0.01);

        // when
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));
        final ValidationResult result = target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).containsOnly("""
                        BidResponse validation `warn`: bidder `bidder` response triggers \
                        secure creative validation for bid bidId, account=account, referrer=unknown, \
                        adm=<tag>http://site.com/creative.jpg</tag>""");
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldIncrementSizeValidationErrMetrics() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(150).h(200));

        // when
        target.validate(givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED);
    }

    @Test
    public void validateShouldIncrementSizeValidationWarnMetrics() {
        // given
        target = new ResponseBidValidator(
                warn, warn, warn,
                currencyConversionService, metrics, jacksonMapper, 0.01);

        // when
        final BidderBid givenBid = givenBid(banner, builder -> builder.w(150).h(200));
        target.validate(givenBid, BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        verify(metrics).updateSizeValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldIncrementSecureValidationErrMetrics() {
        // given
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));

        // when
        target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker)
                .rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE);
    }

    @Test
    public void validateShouldIncrementSecureValidationWarnMetrics() {
        // given
        target = new ResponseBidValidator(
                warn, warn, warn,
                currencyConversionService, metrics, jacksonMapper, 0.01);
        final BidderBid givenBid = givenBid(banner, builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>"));

        // when
        target.validate(
                givenBid,
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(impBuilder -> impBuilder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldNotFailOnSeatValidationWhenSeatEqualsIgnoringCaseToBidder() {
        // when
        final ValidationResult result = target.validate(
                givenBid(banner, identity()).toBuilder().seat("biDDEr").build(),
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
        final BidderBid givenBid = givenBid(banner, identity()).toBuilder().seat("seat").build();
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
        verify(bidRejectionTracker).rejectBid(givenBid, BidRejectionReason.RESPONSE_REJECTED_GENERAL);
    }

    @Test
    public void validateShouldNotFailOnSeatValidationWhenSeatIsAllowed() {
        // given
        final BidderBid givenBid = givenBid(banner, identity()).toBuilder().seat("seat").build();
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

    @Test
    public void validateShouldSkipAdPoddingValidationWhenGlobalConfigIsSkip() {
        // given
        target = new ResponseBidValidator(
                enforce, enforce, skip,
                currencyConversionService, metrics, jacksonMapper, 0.01);

        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(0));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity())),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldSkipAdPoddingValidationWhnAccountConfigIsSkip() {
        // given
        final Account accountWithSkip = givenAccount(accountBuilder -> accountBuilder
                .auction(AccountAuctionConfig.builder()
                        .bidValidations(AccountBidValidationConfig.of(skip, skip))
                        .build()));

        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(0));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity()), accountWithSkip),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldSkipAdPoddingIfVideoObjectHasNoPodIdForVideoBid() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(0));
        final Imp videoImpNoPodId = givenVideoImp(identity(), videoBuilder -> videoBuilder.podid(null));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImpNoPodId, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldSkipAdPoddingIfAudioObjectHasNoPodIdForAudioBid() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(0));
        final Imp audioImpNoPodId = givenAudioImp(identity(), audioBuilder -> audioBuilder.podid(null));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImpNoPodId, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldFailAdPoddingWhenBidDurationIsZeroForVideo() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(0));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenBidDurationIsZeroForAudio() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(0));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(givenAudioImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldUseVideoBidMetaDurationWhenBidDurIsNullAndFailForZeroMetaDur() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("dur", 0)));

        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(null).ext(bidExt));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldUseAudioBidMetaDurationWhenBidDurIsNullAndFailForZeroMetaDur() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("dur", 0)));

        final BidderBid videoBid = givenBid(BidType.audio, builder -> builder.dur(null).ext(bidExt));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenAudioImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenVideoBidExtIsNullAndBidDurIsNull() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(null).ext(null));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenVideoImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenAudioBidExtIsNullAndBidDurIsNull() {
        // given
        final BidderBid videoBid = givenBid(BidType.audio, builder -> builder.dur(null).ext(null));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(givenAudioImp(identity(), identity()), givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenVideoBidDurationNotInRequiredDurations() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(10));
        final Imp videoImp = givenVideoImp(identity(), builder -> builder.rqddurs(List.of(5, 15)));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenAudioBidDurationNotInRequiredDurations() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(10));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder.rqddurs(List.of(5, 15)));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenVideoBidDurationLessThanMinDuration() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(3));
        final Imp videoImp = givenVideoImp(identity(), videoBuilder -> videoBuilder.minduration(5));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenAudioBidDurationLessThanMinDuration() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(3));
        final Imp audioImp = givenVideoImp(identity(), videoBuilder -> videoBuilder.minduration(5));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenVideoBidDurationGreaterThanMaxDuration() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(35));
        final Imp videoImp = givenVideoImp(identity(), videoBuilder -> videoBuilder.maxduration(20));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenAudioBidDurationGreaterThanMaxDuration() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(35));
        final Imp audioImp = givenAudioImp(identity(), videoBuilder -> videoBuilder.maxduration(20));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenVideoBidDurationGreaterThanHighestRequestDurationBucket() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(25));
        final Imp videoImp = givenVideoImp(identity(), builder -> builder.rqddurs(singletonList(25)));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().durationrangesec(asList(5, 10, 20)).build())
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(videoImp))
                .cur(singletonList("USD")).ext(extRequest).build();

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(bidRequest, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=unknown");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenAudioBidDurationGreaterThanHighestRequestDurationBucket() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(25));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder.rqddurs(singletonList(25)));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().durationrangesec(asList(5, 10, 20)).build())
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(audioImp))
                .cur(singletonList("USD")).ext(extRequest).build();

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(bidRequest, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=unknown");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
    }

    @Test
    public void validateShouldFailAdPoddingWhenMinCpmPerSecCheckFailsForVideo() {
        // given
        final BigDecimal bidPrice = BigDecimal.valueOf(1.0);
        final Integer duration = 10;
        final BigDecimal mincpmpersec = BigDecimal.valueOf(0.05);

        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(duration).price(bidPrice));
        final Imp videoImp = givenVideoImp(identity(), videoBuilder -> videoBuilder
                .rqddurs(singletonList(duration))
                .mincpmpersec(mincpmpersec));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(videoBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
        verify(currencyConversionService).convertCurrency(eq(new BigDecimal("0.50")), any(), eq("USD"), eq("USD"));
    }

    @Test
    public void validateShouldFailAdPoddingWhenMinCpmPerSecCheckFailsForAudio() {
        // given
        final BigDecimal bidPrice = BigDecimal.valueOf(1.0);
        final Integer duration = 10;
        final BigDecimal mincpmpersec = BigDecimal.valueOf(0.05);

        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(duration).price(bidPrice));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder
                .rqddurs(singletonList(duration))
                .mincpmpersec(mincpmpersec));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.getErrors()).containsExactly(
                "BidResponse validation `enforce`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.err);
        verify(bidRejectionTracker).rejectBid(audioBid, BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
        verify(currencyConversionService).convertCurrency(eq(new BigDecimal("0.50")), any(), eq("USD"), eq("USD"));
    }

    @Test
    public void validateShouldPassAdPoddingWhenMinCpmPerSecIsNullForVideo() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(15).price(BigDecimal.ONE));
        final Imp videoImp = givenVideoImp(identity(), builder -> builder
                .rqddurs(singletonList(15)).mincpmpersec(null));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
        verify(currencyConversionService, never()).convertCurrency(any(), any(), any(), any());
    }

    @Test
    public void validateShouldPassAdPoddingWhenMinCpmPerSecIsNullForAudio() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(15).price(BigDecimal.ONE));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder
                .rqddurs(singletonList(15)).mincpmpersec(null));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(audioImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
        verify(currencyConversionService, never()).convertCurrency(any(), any(), any(), any());
    }

    @Test
    public void validateShouldPassAdPoddingWhenMinCpmPerSecCheckPassesForVideo() {
        // given
        final Integer duration = 15;
        final BigDecimal bidPrice = BigDecimal.valueOf(1.0);
        final BigDecimal mincpmpersec = BigDecimal.valueOf(0.10);

        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(duration).price(bidPrice));
        final Imp videoImp = givenVideoImp(identity(), videoBuilder -> videoBuilder
                .rqddurs(singletonList(duration)).minduration(10).maxduration(20).mincpmpersec(mincpmpersec));

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldPassAdPoddingWhenMinCpmPerSecCheckPassesForAudio() {
        // given
        final Integer duration = 15;
        final BigDecimal bidPrice = BigDecimal.valueOf(1.0);
        final BigDecimal mincpmpersec = BigDecimal.valueOf(0.10);

        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(duration).price(bidPrice));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder
                .rqddurs(singletonList(duration)).minduration(10).maxduration(20).mincpmpersec(mincpmpersec));
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(audioImp))
                .cur(singletonList("USD")).build();

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(bidRequest, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldPassAdPoddingForValidVideoBidWithAllChecksPassing() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(15).price(BigDecimal.ONE));
        final Imp videoImp = givenVideoImp(identity(), builder -> builder.minduration(10).maxduration(20));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().durationrangesec(asList(10, 15, 20)).build())
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(videoImp))
                .cur(singletonList("USD")).ext(extRequest).build();

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(bidRequest, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldPassAdPoddingForValidAudioBidWithAllChecksPassing() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(15).price(BigDecimal.ONE));
        final Imp audioImp = givenAudioImp(identity(), builder -> builder.minduration(10).maxduration(20));

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().durationrangesec(asList(10, 15, 20)).build())
                .build());
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(audioImp))
                .cur(singletonList("USD")).ext(extRequest).build();

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(bidRequest, givenAccountWithAdPodEnforcement(enforce)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        verify(metrics, never()).updateAdPoddingValidationMetrics(anyString(), anyString(), any());
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldWarnAdPoddingWhenVideoBidDurationIsZeroAndEnforcementIsWarn() {
        // given
        final BidderBid videoBid = givenBid(BidType.video, builder -> builder.dur(0));
        final Imp videoImp = givenVideoImp(identity(), identity());

        // when
        final ValidationResult result = target.validate(
                videoBid,
                BIDDER_NAME,
                givenAuctionContext(videoImp, givenAccountWithAdPodEnforcement(warn)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).containsExactly(
                "BidResponse validation `warn`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void validateShouldWarnAdPoddingWhenAudioBidDurationIsZeroAndEnforcementIsWarn() {
        // given
        final BidderBid audioBid = givenBid(BidType.audio, builder -> builder.dur(0));

        // when
        final ValidationResult result = target.validate(
                audioBid,
                BIDDER_NAME,
                givenAuctionContext(givenAudioImp(identity(), identity()), givenAccountWithAdPodEnforcement(warn)),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings()).containsExactly(
                "BidResponse validation `warn`: bidder `bidder` response triggers ad podding"
                        + " validation for bid bidId, account=account, referrer=referrer");
        verify(metrics).updateAdPoddingValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
        verifyNoInteractions(bidRejectionTracker);
    }

    private static BidderBid givenBid(BidType type, String bidCurrency, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidderBid.of(bidCustomizer.apply(Bid.builder()
                                .id("bidId")
                                .adm("adm")
                                .nurl("nurl")
                                .impid("impId")
                                .crid("crid")
                                .w(1)
                                .h(1)
                                .price(BigDecimal.ONE)
                                .dur(15)
                                .adm("<tag>https://site.com/creative.jpg</tag>"))
                        .build(),
                type,
                bidCurrency);
    }

    private static BidderBid givenBid(BidType type, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return givenBid(type, "USD", bidCustomizer);
    }

    private static Imp givenVideoImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                     UnaryOperator<Video.VideoBuilder> videoCustomizer) {

        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .video(videoCustomizer.apply(Video.builder().podid(1)).build()))
                .build();
    }

    private static Imp givenAudioImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                     UnaryOperator<Audio.AudioBuilder> audioCustomizer) {

        return impCustomizer.apply(Imp.builder()
                .id("impId")
                .audio(audioCustomizer.apply(Audio.builder().podid(1)).build())).build();
    }

    private BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        final ObjectNode ext = mapper.createObjectNode().set(
                "prebid", mapper.createObjectNode().set(
                        "bidder", mapper.createObjectNode().set(
                                BIDDER_NAME, mapper.createObjectNode().put(
                                        "dealsonly", true))));

        final Imp imp = impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(100).h(200).build(),
                                        Format.builder().w(50).h(50).build()))
                                .build())
                        .ext(ext))
                .build();

        return BidRequest.builder().imp(singletonList(imp)).cur(singletonList("USD")).build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .bidRejectionTrackers(Map.of(BIDDER_NAME, bidRejectionTracker))
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

    private AuctionContext givenAuctionContext(Imp imp, Account account) {
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .site(Site.builder().page("referrer").build())
                .cur(singletonList("USD"))
                .build();
        return givenAuctionContext(bidRequest, account);
    }

    private AuctionContext givenAuctionContext(Imp imp) {
        return givenAuctionContext(imp, givenAccount());
    }

    private static Account givenAccount() {
        return givenAccount(identity());
    }

    private static Account givenAccount(UnaryOperator<Account.AccountBuilder> accountCustomizer) {
        return accountCustomizer.apply(Account.builder().id(ACCOUNT_ID)).build();
    }

    private Account givenAccountWithAdPodEnforcement(BidValidationEnforcement enforcement) {
        return givenAccount(accountBuilder -> accountBuilder
                .auction(AccountAuctionConfig.builder()
                        .bidValidations(AccountBidValidationConfig.of(skip, enforcement))
                        .build()));
    }
}
