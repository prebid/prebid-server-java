package org.prebid.server.validation;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.validation.model.Size;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};

    private final boolean shouldValidateBanner;
    private final List<Size> bannerAllowedSizes;
    private final boolean shouldValidateSecureMarkup;

    public ResponseBidValidator(boolean shouldValidateBanner,
                                List<String> bannerAllowedSizes,
                                boolean shouldValidateSecureMarkup) {

        this.shouldValidateBanner = shouldValidateBanner;
        this.bannerAllowedSizes = toSizes(bannerAllowedSizes);
        this.shouldValidateSecureMarkup = shouldValidateSecureMarkup;
    }

    public ValidationResult validate(BidderBid bidderBid, BidRequest bidRequest) {
        try {
            validateCommonFields(bidderBid.getBid());

            if (shouldValidateBanner(bidderBid)) {
                validateBannerFields(bidderBid);
            }

            if (shouldValidateSecureMarkup()) {
                validateSecureMarkup(bidderBid, bidRequest);
            }
        } catch (ValidationException e) {
            return ValidationResult.error(e.getMessage());
        }
        return ValidationResult.success();
    }

    private static List<Size> toSizes(List<String> bannerAllowedSizes) {
        return CollectionUtils.emptyIfNull(bannerAllowedSizes).stream()
                .map(ResponseBidValidator::parseSize)
                .collect(Collectors.toList());
    }

    private static Size parseSize(String size) {
        final String[] widthAndHeight = size.split("x");
        if (widthAndHeight.length != 2) {
            throw new IllegalArgumentException(String.format(
                    "Invalid size format: %s. Should be '[width]x[height]'", size));
        }

        try {
            return Size.of(
                    Integer.parseInt(widthAndHeight[0]),
                    Integer.parseInt(widthAndHeight[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid size format", e);
        }
    }

    private static void validateCommonFields(Bid bid) throws ValidationException {
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

    private boolean shouldValidateBanner(BidderBid bidderBid) {
        return bidderBid.getType() == BidType.banner && shouldValidateBanner;
    }

    private void validateBannerFields(BidderBid bidderBid) throws ValidationException {
        final Bid bid = bidderBid.getBid();

        if (bannerSizeIsNotValid(bid)) {
            throw new ValidationException(
                    "Bid \"%s\" has 'w' and 'h' that are not valid. Bid dimensions: '%dx%d'",
                    bid.getId(), bid.getW(), bid.getH());
        }
    }

    private boolean bannerSizeIsNotValid(Bid bid) {
        return bannerAllowedSizes.stream().noneMatch(size -> bidHasEqualSize(bid, size));
    }

    private static boolean bidHasEqualSize(Bid bid, Size size) {
        return Objects.equals(bid.getW(), size.getWidth()) && Objects.equals(bid.getH(), size.getHeight());
    }

    private boolean shouldValidateSecureMarkup() {
        return shouldValidateSecureMarkup;
    }

    private static void validateSecureMarkup(BidderBid bidderBid, BidRequest bidRequest) throws ValidationException {
        final Bid bid = bidderBid.getBid();
        final Imp imp = findCorrespondingImp(bidRequest, bidderBid.getBid());

        if (isImpSecure(imp) && markupIsNotSecure(bid)) {
            throw new ValidationException(
                    "Bid \"%s\" has has insecure creative but should be in secure context", bid.getId());
        }
    }

    private static Imp findCorrespondingImp(BidRequest bidRequest, Bid bid) throws ValidationException {
        return bidRequest.getImp().stream()
                .filter(curImp -> Objects.equals(curImp.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Bid \"%s\" has no corresponding imp in request", bid.getId()));
    }

    public static boolean isImpSecure(Imp imp) {
        return Objects.equals(imp.getSecure(), 1);
    }

    private static boolean markupIsNotSecure(Bid bid) {
        return StringUtils.containsAny(bid.getAdm(), INSECURE_MARKUP_MARKERS);
    }
}
