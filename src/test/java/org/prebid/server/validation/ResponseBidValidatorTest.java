package org.prebid.server.validation;

import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ResponseBidValidatorTest {

    private ResponseBidValidator responseBidValidator;

    @Before
    public void setUp() {
        responseBidValidator = new ResponseBidValidator();
    }

    @Test
    public void validateShouldFailedIfBidderBidCurrencyIsIncorrect() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                responseBidValidator.validate(BidderBid.of(
                        Bid.builder()
                                .id("bidId1")
                                .impid("impId1")
                                .crid("crid1")
                                .price(BigDecimal.ONE).build(),
                        null,
                        "USDD")));
    }

    @Test
    public void validateShouldFailedIfMissingBid() {
        final ValidationResult result = responseBidValidator.validate(BidderBid.of(null, null, "USD"));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Empty bid object submitted.");
    }

    @Test
    public void validateShouldFailedIfBidHasNoId() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.id(null)));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid missing required field 'id'");
    }

    @Test
    public void validateShouldFailedIfBidHasNoImpId() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.impid(null)));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" missing required field 'impid'");
    }

    @Test
    public void validateShouldFailedIfBidHasNoPrice() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.price(null)));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" does not contain a 'price'");
    }

    @Test
    public void validateShouldFailedIfBidHasNegativePrice() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.price(
                BigDecimal.valueOf(-1))));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" `price `has negative value");
    }

    @Test
    public void validateShouldFailedIfNonDealBidHasZeroPrice() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.price(
                BigDecimal.valueOf(0))));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Non deal bid \"bidId1\" has 0 price");
    }

    @Test
    public void validateShouldSuccessForDealZeroPriceBid() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.price(
                BigDecimal.valueOf(0)).dealid("dealId")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldFailedIfBidHasNoCrid() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.crid(null)));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" missing creative ID");
    }

    @Test
    public void validateShouldReturnSuccessfulResultForValidBid() {
        final ValidationResult result = responseBidValidator.validate(givenBid(identity()));

        assertThat(result.hasErrors()).isFalse();
    }

    private static BidderBid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer, BidType mediaType) {
        final Bid.BidBuilder bidBuilder = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .crid("crid1")
                .price(BigDecimal.ONE);
        return BidderBid.of(bidCustomizer.apply(bidBuilder).build(), mediaType, "USD");
    }

    private static BidderBid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return givenBid(bidCustomizer, null);
    }
}
