package org.prebid.server.validation;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};
    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};

    private final BidValidationEnforcement bannerMaxSizeEnforcement;
    private final BidValidationEnforcement secureMarkupEnforcement;
    private final Metrics metrics;

    public ResponseBidValidator(BidValidationEnforcement bannerMaxSizeEnforcement,
                                BidValidationEnforcement secureMarkupEnforcement,
                                Metrics metrics) {

        this.bannerMaxSizeEnforcement = Objects.requireNonNull(bannerMaxSizeEnforcement);
        this.secureMarkupEnforcement = Objects.requireNonNull(secureMarkupEnforcement);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public ValidationResult validate(
            BidderBid bidderBid, BidderRequest bidderRequest, Account account, BidderAliases aliases) {

        final List<String> warnings = new ArrayList<>();

        try {
            validateCommonFields(bidderBid.getBid());

            if (bidderBid.getType() == BidType.banner) {
                warnings.addAll(validateBannerFields(bidderBid, bidderRequest, account, aliases));
            }

            warnings.addAll(validateSecureMarkup(bidderBid, bidderRequest, account, aliases));
        } catch (ValidationException e) {
            return ValidationResult.error(warnings, e.getMessage());
        }
        return ValidationResult.success(warnings);
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

    private List<String> validateBannerFields(BidderBid bidderBid,
                                              BidderRequest bidderRequest,
                                              Account account,
                                              BidderAliases aliases) throws ValidationException {

        final Bid bid = bidderBid.getBid();

        final BidValidationEnforcement bannerMaxSizeEnforcement = effectiveBannerMaxSizeEnforcement(account);
        if (bannerMaxSizeEnforcement != BidValidationEnforcement.skip && bannerSizeIsNotValid(bid, bidderRequest)) {
            return singleWarningOrValidationException(
                    bannerMaxSizeEnforcement,
                    metricName -> metrics.updateSizeValidationMetrics(
                            aliases.resolveBidder(bidderRequest.getBidder()), account.getId(), metricName),
                    "Bid \"%s\" has 'w' and 'h' that are not valid. Bid dimensions: '%dx%d'",
                    bid.getId(), bid.getW(), bid.getH());
        }

        return Collections.emptyList();
    }

    private BidValidationEnforcement effectiveBannerMaxSizeEnforcement(Account account) {
        final AccountBidValidationConfig validationConfig = account.getBidValidations();
        final BidValidationEnforcement accountBannerMaxSizeEnforcement =
                validationConfig != null ? validationConfig.getBannerMaxSizeEnforcement() : null;

        return ObjectUtils.defaultIfNull(accountBannerMaxSizeEnforcement, bannerMaxSizeEnforcement);
    }

    private static boolean bannerSizeIsNotValid(Bid bid, BidderRequest bidderRequest) throws ValidationException {
        final Format maxSize = maxSizeForBanner(bid, bidderRequest);
        final Integer bidW = bid.getW();
        final Integer bidH = bid.getH();

        return bidW == null || bidW > maxSize.getW()
                || bidH == null || bidH > maxSize.getH();
    }

    private static Format maxSizeForBanner(Bid bid, BidderRequest bidderRequest) throws ValidationException {
        int maxW = 0;
        int maxH = 0;
        for (final Format size : bannerFormats(bid, bidderRequest)) {
            maxW = Math.max(0, size.getW());
            maxH = Math.max(0, size.getH());
        }

        return Format.builder().w(maxW).h(maxH).build();
    }

    private static List<Format> bannerFormats(Bid bid, BidderRequest bidderRequest) throws ValidationException {
        final Imp imp = findCorrespondingImp(bidderRequest.getBidRequest(), bid);
        final Banner banner = imp.getBanner();

        return ListUtils.emptyIfNull(banner != null ? banner.getFormat() : null);
    }

    private List<String> validateSecureMarkup(BidderBid bidderBid,
                                              BidderRequest bidderRequest,
                                              Account account,
                                              BidderAliases aliases) throws ValidationException {

        if (secureMarkupEnforcement == BidValidationEnforcement.skip) {
            return Collections.emptyList();
        }

        final Bid bid = bidderBid.getBid();
        final Imp imp = findCorrespondingImp(bidderRequest.getBidRequest(), bidderBid.getBid());

        if (isImpSecure(imp) && markupIsNotSecure(bid)) {
            return singleWarningOrValidationException(
                    secureMarkupEnforcement,
                    metricName -> metrics.updateSecureValidationMetrics(
                            aliases.resolveBidder(bidderRequest.getBidder()), account.getId(), metricName),
                    "Bid \"%s\" has insecure creative but should be in secure context",
                    bid.getId());
        }

        return Collections.emptyList();
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
        final String adm = bid.getAdm();

        return StringUtils.containsAny(adm, INSECURE_MARKUP_MARKERS)
                || !StringUtils.containsAny(adm, SECURE_MARKUP_MARKERS);
    }

    private static List<String> singleWarningOrValidationException(BidValidationEnforcement enforcement,
                                                                   Consumer<MetricName> metricsRecorder,
                                                                   String message,
                                                                   Object... args) throws ValidationException {

        switch (enforcement) {
            case enforce:
                metricsRecorder.accept(MetricName.err);
                throw new ValidationException(message, args);
            case warn:
                metricsRecorder.accept(MetricName.warn);
                return Collections.singletonList(String.format(message, args));
            default:
                throw new IllegalStateException(String.format("Unexpected enforcement: %s", enforcement));
        }
    }
}
