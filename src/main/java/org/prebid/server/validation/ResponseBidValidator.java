package org.prebid.server.validation;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountBidValidationConfig;
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
    private final Metrics metrics;
    private final JacksonMapper mapper;

    public ResponseBidValidator(boolean shouldValidateBanner,
                                List<String> bannerAllowedSizes,
                                boolean shouldValidateSecureMarkup,
                                Metrics metrics,
                                JacksonMapper mapper) {

        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.shouldValidateBanner = shouldValidateBanner;
        this.bannerAllowedSizes = toSizes(bannerAllowedSizes);
        this.shouldValidateSecureMarkup = shouldValidateSecureMarkup;
    }

    public ValidationResult validate(BidderBid bidderBid, BidderRequest bidderRequest, Account account) {
        try {
            validateCommonFields(bidderBid.getBid());

            if (shouldValidateBanner(bidderBid)) {
                validateBannerFields(bidderBid, bidderRequest, account);
            }

            if (shouldValidateSecureMarkup) {
                validateSecureMarkup(bidderBid, bidderRequest, account);
            }
        } catch (ValidationException e) {
            return ValidationResult.error(e.getMessage());
        }
        return ValidationResult.success();
    }

    private List<Size> toSizes(List<String> bannerAllowedSizes) {
        return CollectionUtils.emptyIfNull(bannerAllowedSizes).stream()
                .map(this::parseSize)
                .collect(Collectors.toList());
    }

    private Size parseSize(String size) {
        try {
            return mapper.decodeValue(StringUtils.wrap(size, '\"'), Size.class);
        } catch (DecodeException e) {
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

    private void validateBannerFields(
            BidderBid bidderBid, BidderRequest bidderRequest, Account account) throws ValidationException {

        final Bid bid = bidderBid.getBid();

        if (bannerSizeIsNotValid(bid, validBannerSizes(account))) {
            metrics.updateValidationErrorMetrics(bidderRequest.getBidder(), account.getId(), MetricName.size);

            throw new ValidationException(
                    "Bid \"%s\" has 'w' and 'h' that are not valid. Bid dimensions: '%dx%d'",
                    bid.getId(), bid.getW(), bid.getH());
        }
    }

    private List<Size> validBannerSizes(Account account) {
        final AccountBidValidationConfig validationConfig = account.getBidValidations();
        final List<Size> accountValidBannerSizes =
                validationConfig != null ? validationConfig.getBannerCreativeAllowedSizes() : null;

        return accountValidBannerSizes != null ? accountValidBannerSizes : bannerAllowedSizes;
    }

    private static boolean bannerSizeIsNotValid(Bid bid, List<Size> validBannerSizes) {
        return validBannerSizes.stream().noneMatch(size -> bidHasEqualSize(bid, size));
    }

    private static boolean bidHasEqualSize(Bid bid, Size size) {
        return Objects.equals(bid.getW(), size.getWidth()) && Objects.equals(bid.getH(), size.getHeight());
    }

    private void validateSecureMarkup(
            BidderBid bidderBid, BidderRequest bidderRequest, Account account) throws ValidationException {

        final Bid bid = bidderBid.getBid();
        final Imp imp = findCorrespondingImp(bidderRequest.getBidRequest(), bidderBid.getBid());

        if (isImpSecure(imp) && markupIsNotSecure(bid)) {
            metrics.updateValidationErrorMetrics(bidderRequest.getBidder(), account.getId(), MetricName.secure);

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
