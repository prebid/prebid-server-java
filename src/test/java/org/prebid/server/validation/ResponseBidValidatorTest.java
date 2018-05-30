package org.prebid.server.validation;

import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class ResponseBidValidatorTest {

    private ResponseBidValidator responseBidValidator;

    @Before
    public void setUp() {
        responseBidValidator = new ResponseBidValidator();
    }

    @Test
    public void validateShouldFailedIfMissingBid() {
        final ValidationResult result = responseBidValidator.validate(null);

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
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
    }

    @Test
    public void validateShouldFailedIfBidHasNegativePrice() {
        final ValidationResult result = responseBidValidator.validate(givenBid(builder -> builder.price(
                BigDecimal.valueOf(-1))));

        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Bid \"bidId1\" does not contain a positive 'price'");
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

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        final Bid.BidBuilder bidBuilder = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .crid("crid1")
                .price(BigDecimal.ONE);
        return bidCustomizer.apply(bidBuilder).build();
    }
}
