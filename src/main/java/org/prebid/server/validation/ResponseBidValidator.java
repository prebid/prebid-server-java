package org.prebid.server.validation;

import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseBidValidator.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    public ValidationResult validate(Bid bid, String bidder, String publisherId, String referer) {
        try {
            validateFieldsFor(bid);
            validateAnticipatedFieldsFro(bid, bidder, publisherId, referer);
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

    private static void validateAnticipatedFieldsFro(Bid bid, String bidder, String publisherId, String referer) {
        if (bid.getW() == null || bid.getH() == null) {
            final String invalidValue = bid.getW() == null ? "bid.h" : "bid.w";
            final String message = String.format("Bid from bidder %s and with publisherId %s and referer %s"
                            + " missing value %s. This value is required for `dimensions` sizes.", bidder, publisherId,
                    referer, invalidValue);
            conditionalLogger.error(message, 100);
        }
    }
}
