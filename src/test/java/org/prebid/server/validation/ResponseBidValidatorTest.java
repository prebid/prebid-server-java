package org.prebid.server.validation;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class ResponseBidValidatorTest {

    private static final List<String> BANNER_ALLOWED_SIZES = asList("1x1", "2x2");

    private ResponseBidValidator responseBidValidator;

    @Before
    public void setUp() {
        responseBidValidator = new ResponseBidValidator(true, BANNER_ALLOWED_SIZES, true);
    }

    @Test
    public void validateShouldFailIfMissingBid() {
        final ValidationResult result = responseBidValidator.validate(
                BidderBid.of(null, null, null), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Empty bid object submitted.");
    }

    @Test
    public void validateShouldFailIfBidHasNoId() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.id(null)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid missing required field 'id'");
    }

    @Test
    public void validateShouldFailIfBidHasNoImpId() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid(null)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" missing required field 'impid'");
    }

    @Test
    public void validateShouldFailIfBidHasNoPrice() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(null)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
    }

    @Test
    public void validateShouldFailIfBidHasNegativePrice() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.price(BigDecimal.valueOf(-1))), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
    }

    @Test
    public void validateShouldFailIfBidHasNoCrid() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.crid(null)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" missing creative ID");
    }

    @Test
    public void validateShouldFailIfBannerBidHasNoWidthAndHeight() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: 'nullxnull'");
    }

    @Test
    public void validateShouldFailIfBannerBidHasNotAllowedWidthAndHeight() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(3).h(3)), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has 'w' and 'h' that are not valid. Bid dimensions: '3x3'");
    }

    @Test
    public void validateShouldReturnSuccessIfNonBannerBidHasAnySize() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(BidType.video, builder -> builder.w(3).h(3)), givenBidRequest(identity()));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailIfBidHasNoCorrespondingImp() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.impid("nonExistentsImpid")), givenBidRequest(identity()));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has no corresponding imp in request");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureCreativeInSecureContext() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidRequest(builder -> builder.secure(1)));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has has insecure creative but should be in secure context");
    }

    @Test
    public void validateShouldFailIfBidHasInsecureEncodedCreativeInSecureContext() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http%3A//site.com/creative.jpg</tag>")),
                givenBidRequest(builder -> builder.secure(1)));

        assertThat(result.getErrors())
                .containsOnly("Bid \"bidId1\" has has insecure creative but should be in secure context");
    }

    @Test
    public void validateShouldReturnSuccessIfBidHasInsecureCreativeInInsecureContext() {
        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidRequest(identity()));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        final ValidationResult result =
                responseBidValidator.validate(givenBid(identity()), givenBidRequest(builder -> builder.secure(1)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfBannerSizeValidationNotEnabled() {
        responseBidValidator = new ResponseBidValidator(false, null, true);

        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.w(null).h(null)),
                givenBidRequest(identity()));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnSuccessIfSecureMarkupValidationNotEnabled() {
        responseBidValidator = new ResponseBidValidator(true, BANNER_ALLOWED_SIZES, false);

        final ValidationResult result = responseBidValidator.validate(
                givenBid(builder -> builder.adm("<tag>>http://site.com/creative.jpg</tag>")),
                givenBidRequest(builder -> builder.secure(1)));

        assertThat(result.hasErrors()).isFalse();
    }

    private BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id("impId1");

        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(impBuilder).build()))
                .build();
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
}
