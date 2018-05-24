package org.prebid.server.validation;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    public ValidationResult validate(Bid bid) {
        try {
            validateFieldsFor(bid);
        } catch (ValidationException e) {
            return ValidationResult.error(e.getMessage());
        }
        return ValidationResult.success();
    }

    private static void validateFieldsFor(Bid bid) throws ValidationException {
        if (bid == null) {
            throw new ValidationException("Empty bid object submitted.");
        }

        final String bidId = bid.getId();
        if (StringUtils.isBlank(bidId)) {
            throw new ValidationException("Bid missing required field 'id'");
        }

        if (StringUtils.isBlank(bid.getImpid())) {
            throw new ValidationException("Bid \"%s\" missing required field 'impid'", bidId);
        }

        final BigDecimal price = bid.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Bid \"%s\" does not contain a positive 'price'", bidId);
        }

        if (StringUtils.isEmpty(bid.getCrid())) {
            throw new ValidationException("Bid \"%s\" missing creative ID", bidId);
        }
    }
}
