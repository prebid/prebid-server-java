package org.prebid.server.validation;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
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
        responseBidValidator = new ResponseBidValidator(enforce, enforce, metrics);

        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);
    }

    @Test
    public void validateShouldFailedIfBidderBidCurrencyIsIncorrect() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                responseBidValidator.validate(
                        BidderBid.of(
                                Bid.builder()
                                        .id("bidId1")
                                        .impid("impId1")
                                        .crid("crid1")
                                        .price(BigDecimal.ONE)
                                        .build(),
                                null,
                                "USDD"),
                        BIDDER_NAME,
                        givenAuctionContext(),
                        bidderAliases));
    }

    @Test
    public void validateShouldFailIfMissingBid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                BidderBid.of(null, null, "USD"), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).containsOnly("Empty bid object submitted.");
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
    public void validateShouldFailIfBidHasNoPrice() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(null)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("Bid \"bidId1\" does not contain a 'price'");
    }

    @Test
    public void validateShouldFailIfBidHasNegativePrice() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(BigDecimal.valueOf(-1))),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("Bid \"bidId1\" `price `has negative value");
    }

    @Test
    public void validateShouldFailedIfNonDealBidHasZeroPrice() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(BigDecimal.valueOf(0))),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Non deal bid \"bidId1\" has 0 price");
    }

    @Test
    public void validateShouldSuccessForDealZeroPriceBid() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(BigDecimal.valueOf(0)).dealid("dealId")),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

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
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: 'nullxnull'");
    }

    @Test
    public void validateShouldFailIfBannerBidWidthIsGreaterThanImposedByImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(150)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: '150x150'");
    }

    @Test
    public void validateShouldFailIfBannerBidHeightIsGreaterThanImposedByImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(50).h(250)), BIDDER_NAME, givenAuctionContext(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: '50x250'");
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
    public void validateShouldReturnSuccessIfBannerBidHasInvalidSizeButAccountDoesNotEnforceValidation() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(150).h(150)),
                BIDDER_NAME,
                givenAuctionContext(
                        givenAccount(builder -> builder.bidValidations(AccountBidValidationConfig.of(skip)))),
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
                .containsOnly("Bid \"bidId1\" has insecure creative but should be in secure context");
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
                .containsOnly("Bid \"bidId1\" has insecure creative but should be in secure context");
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
                .containsOnly("Bid \"bidId1\" has insecure creative but should be in secure context");
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
        responseBidValidator = new ResponseBidValidator(skip, enforce, metrics);

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
        responseBidValidator = new ResponseBidValidator(warn, enforce, metrics);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)),
                BIDDER_NAME,
                givenAuctionContext(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: 'nullxnull'");
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        // given
        responseBidValidator = new ResponseBidValidator(enforce, skip, metrics);

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
        responseBidValidator = new ResponseBidValidator(enforce, warn, metrics);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getWarnings())
                .containsOnly("Bid \"bidId1\" has insecure creative but should be in secure context");
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
        responseBidValidator = new ResponseBidValidator(warn, warn, metrics);

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
        responseBidValidator = new ResponseBidValidator(warn, warn, metrics);

        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>http://site.com/creative.jpg</tag>")),
                BIDDER_NAME,
                givenAuctionContext(givenBidRequest(builder -> builder.secure(1))),
                bidderAliases);

        // then
        verify(metrics).updateSecureValidationMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.warn);
    }

    private static BidderBid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return givenBid(BidType.banner, bidCustomizer);
    }

    private static BidderBid givenBid(BidType type, Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        final Bid.BidBuilder bidBuilder = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .crid("crid1")
                .w(1)
                .h(1)
                .adm("<tag>https://site.com/creative.jpg</tag>")
                .price(BigDecimal.ONE);

        return BidderBid.of(bidCustomizer.apply(bidBuilder).build(), type, "USD");
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

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(100).h(200).build()))
                        .build());

        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(impBuilder).build()))
                .build();
    }

    private static Account givenAccount() {
        return givenAccount(identity());
    }

    private static Account givenAccount(Function<Account.AccountBuilder, Account.AccountBuilder> accountCustomizer) {
        return accountCustomizer.apply(Account.builder().id(ACCOUNT_ID)).build();
    }
}
