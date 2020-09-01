package org.prebid.server.validation;

import com.iab.openrtb.request.BidRequest;
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
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.validation.model.Size;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ResponseBidValidatorTest extends VertxTest {

    private static final List<String> BANNER_ALLOWED_SIZES = asList("1x1", "2x2");
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
        responseBidValidator = new ResponseBidValidator(true, BANNER_ALLOWED_SIZES, true, metrics, jacksonMapper);

        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);
    }

    @Test
    public void validateShouldFailIfMissingBid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                BidderBid.of(null, null, null), givenBidderRequest(identity()), givenAccount(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Empty bid object submitted.");
    }

    @Test
    public void validateShouldFailIfBidHasNoId() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.id(null)), givenBidderRequest(identity()), givenAccount(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid missing required field 'id'");
    }

    @Test
    public void validateShouldFailIfBidHasNoImpId() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid(null)), givenBidderRequest(identity()), givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" missing required field 'impid'");
    }

    @Test
    public void validateShouldFailIfBidHasNoPrice() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(null)), givenBidderRequest(identity()), givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
    }

    @Test
    public void validateShouldFailIfBidHasNegativePrice() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(BigDecimal.valueOf(-1))),
                givenBidderRequest(identity()),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
    }

    @Test
    public void validateShouldFailIfBidHasNoCrid() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.crid(null)), givenBidderRequest(identity()), givenAccount(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" missing creative ID");
    }

    @Test
    public void validateShouldFailIfBannerBidHasNoWidthAndHeight() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)), givenBidderRequest(identity()), givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: 'nullxnull'");
    }

    @Test
    public void validateShouldFailIfBannerBidHasNotAllowedWidthAndHeight() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(3).h(3)), givenBidderRequest(identity()), givenAccount(), bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: '3x3'");
    }

    @Test
    public void validateShouldReturnSuccessIfNonBannerBidHasAnySize() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)),
                givenBidderRequest(identity()),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfBannerBidHasAllowedByAccountWidthAndHeight() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(3).h(3)),
                givenBidderRequest(identity()),
                givenAccount(builder -> builder.bidValidations(
                        AccountBidValidationConfig.of(singletonList(Size.of(3, 3))))),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailIfBidHasNoCorrespondingImp() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid("nonExistentsImpid")),
                givenBidderRequest(identity()),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has no corresponding imp in request");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureCreativeInSecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidderRequest(builder -> builder.secure(1)),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has has insecure creative but should be in secure context");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureEncodedCreativeInSecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http%3A//site.com/creative.jpg</tag>")),
                givenBidderRequest(builder -> builder.secure(1)),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has has insecure creative but should be in secure context");
    }

    @Test
    public void validateShouldReturnSuccessIfBidHasInsecureCreativeInInsecureContext() {
        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidderRequest(identity()),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        // when
        final ValidationResult result =
                responseBidValidator.validate(
                        givenBid(identity()),
                        givenBidderRequest(builder -> builder.secure(1)),
                        givenAccount(),
                        bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfBannerSizeValidationNotEnabled() {
        // given
        responseBidValidator = new ResponseBidValidator(false, null, true, metrics, jacksonMapper);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)),
                givenBidderRequest(identity()),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        // given
        responseBidValidator = new ResponseBidValidator(true, BANNER_ALLOWED_SIZES, false, metrics, jacksonMapper);

        // when
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidderRequest(builder -> builder.secure(1)),
                givenAccount(),
                bidderAliases);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldIncrementValidationErrorSizeMetrics() {
        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.w(3).h(3)), givenBidderRequest(identity()), givenAccount(), bidderAliases);

        // then
        verify(metrics).updateValidationErrorMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.size);
    }

    @Test
    public void validateShouldIncrementValidationErrorSecureMetrics() {
        // when
        responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidderRequest(builder -> builder.secure(1)),
                givenAccount(),
                bidderAliases);

        // then
        verify(metrics).updateValidationErrorMetrics(BIDDER_NAME, ACCOUNT_ID, MetricName.secure);
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

        return BidderBid.of(bidCustomizer.apply(bidBuilder).build(), type, null);
    }

    private static BidderRequest givenBidderRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id("impId1");

        return BidderRequest.of(
                BIDDER_NAME,
                BidRequest.builder()
                        .imp(singletonList(impCustomizer.apply(impBuilder).build()))
                        .build());
    }

    private static Account givenAccount() {
        return givenAccount(identity());
    }

    private static Account givenAccount(Function<Account.AccountBuilder, Account.AccountBuilder> accountCustomizer) {
        return accountCustomizer.apply(Account.builder().id(ACCOUNT_ID)).build();
    }
}
