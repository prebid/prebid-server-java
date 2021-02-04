package org.prebid.server.validation;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.log.ConditionalLogger;
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
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseBidValidator.class);
    private static final ConditionalLogger SECURE_CREATIVE_LOGGER = new ConditionalLogger("secure_creatives_validation",
            logger);
    private static final ConditionalLogger CREATIVE_SIZE_LOGGER = new ConditionalLogger("creative_size_validation",
            logger);

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};
    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};
    private static final double LOG_SAMPLING_RATE = 0.01;

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
            BidderBid bidderBid, String bidder, AuctionContext auctionContext, BidderAliases aliases) {

        final List<String> warnings = new ArrayList<>();

        try {
            validateCommonFields(bidderBid.getBid());
            validateCurrency(bidderBid.getBidCurrency());

            if (bidderBid.getType() == BidType.banner) {
                warnings.addAll(validateBannerFields(bidderBid, bidder, auctionContext, aliases));
            }

            warnings.addAll(validateSecureMarkup(bidderBid, bidder, auctionContext, aliases));
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
        if (price == null) {
            throw new ValidationException("Bid \"%s\" does not contain a 'price'", bidId);
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Bid \"%s\" `price `has negative value", bidId);
        }

        if (price.compareTo(BigDecimal.ZERO) == 0 && StringUtils.isBlank(bid.getDealid())) {
            throw new ValidationException("Non deal bid \"%s\" has 0 price", bidId);
        }

        if (StringUtils.isEmpty(bid.getCrid())) {
            throw new ValidationException("Bid \"%s\" missing creative ID", bidId);
        }
    }

    private static void validateCurrency(String currency) {
        try {
            if (StringUtils.isNotBlank(currency)) {
                Currency.getInstance(currency);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("BidResponse currency is not valid: %s", currency), e);
        }
    }

    private List<String> validateBannerFields(BidderBid bidderBid,
                                              String bidder,
                                              AuctionContext auctionContext,
                                              BidderAliases aliases) throws ValidationException {

        final Bid bid = bidderBid.getBid();
        final Account account = auctionContext.getAccount();

        final BidValidationEnforcement bannerMaxSizeEnforcement = effectiveBannerMaxSizeEnforcement(account);
        if (bannerMaxSizeEnforcement != BidValidationEnforcement.skip) {
            final BidRequest bidRequest = auctionContext.getBidRequest();
            final Format maxSize = maxSizeForBanner(bid, bidRequest);
            if (bannerSizeIsNotValid(bid, maxSize)) {
                final String accountId = auctionContext.getAccount().getId();
                final String message = String.format(
                        "BidResponse validation `%s`: bidder `%s` response triggers creative size validation for bid "
                                + "%s, account=%s, referrer=%s, max imp size='%dx%d', bid response size='%dx%d'",
                        bannerMaxSizeEnforcement, bidder, bid.getId(), accountId, getReferer(bidRequest),
                        maxSize.getW(), maxSize.getH(), bid.getW(), bid.getH());
                return singleWarningOrValidationException(
                        bannerMaxSizeEnforcement,
                        metricName -> metrics.updateSizeValidationMetrics(
                                aliases.resolveBidder(bidder), account.getId(), metricName),
                        CREATIVE_SIZE_LOGGER, message);
            }
        }
        return Collections.emptyList();
    }

    private BidValidationEnforcement effectiveBannerMaxSizeEnforcement(Account account) {
        final AccountBidValidationConfig validationConfig = account.getBidValidations();
        final BidValidationEnforcement accountBannerMaxSizeEnforcement =
                validationConfig != null ? validationConfig.getBannerMaxSizeEnforcement() : null;

        return ObjectUtils.defaultIfNull(accountBannerMaxSizeEnforcement, bannerMaxSizeEnforcement);
    }

    private static boolean bannerSizeIsNotValid(Bid bid, Format maxSize) {
        final Integer bidW = bid.getW();
        final Integer bidH = bid.getH();
        return bidW == null || bidW > maxSize.getW()
                || bidH == null || bidH > maxSize.getH();
    }

    private static Format maxSizeForBanner(Bid bid, BidRequest bidRequest) throws ValidationException {
        int maxW = 0;
        int maxH = 0;
        for (final Format size : bannerFormats(bid, bidRequest)) {
            maxW = Math.max(maxW, size.getW());
            maxH = Math.max(maxH, size.getH());
        }

        return Format.builder().w(maxW).h(maxH).build();
    }

    private static List<Format> bannerFormats(Bid bid, BidRequest bidRequest) throws ValidationException {
        final Imp imp = findCorrespondingImp(bidRequest, bid);
        final Banner banner = imp.getBanner();

        return ListUtils.emptyIfNull(banner != null ? banner.getFormat() : null);
    }

    private List<String> validateSecureMarkup(BidderBid bidderBid,
                                              String bidder,
                                              AuctionContext auctionContext,
                                              BidderAliases aliases) throws ValidationException {

        if (secureMarkupEnforcement == BidValidationEnforcement.skip) {
            return Collections.emptyList();
        }

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Bid bid = bidderBid.getBid();
        final Imp imp = findCorrespondingImp(bidRequest, bidderBid.getBid());
        final String accountId = auctionContext.getAccount().getId();
        final String referer = getReferer(bidRequest);
        final String adm = bid.getAdm();

        if (isImpSecure(imp) && markupIsNotSecure(adm)) {
            final String message = String.format("BidResponse validation `%s`: bidder `%s` response triggers secure"
                            + " creative validation for bid %s, account=%s, referrer=%s, adm=%s",
                    secureMarkupEnforcement, bidder, bid.getId(), accountId, referer, adm);
            return singleWarningOrValidationException(
                    secureMarkupEnforcement,
                    metricName -> metrics.updateSecureValidationMetrics(
                            aliases.resolveBidder(bidder), auctionContext.getAccount().getId(), metricName),
                    SECURE_CREATIVE_LOGGER, message
            );
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

    private static boolean markupIsNotSecure(String adm) {
        return StringUtils.containsAny(adm, INSECURE_MARKUP_MARKERS)
                || !StringUtils.containsAny(adm, SECURE_MARKUP_MARKERS);
    }

    private static List<String> singleWarningOrValidationException(BidValidationEnforcement enforcement,
                                                                   Consumer<MetricName> metricsRecorder,
                                                                   ConditionalLogger conditionalLogger,
                                                                   String message) throws ValidationException {
        switch (enforcement) {
            case enforce:
                metricsRecorder.accept(MetricName.err);
                conditionalLogger.warn(message, LOG_SAMPLING_RATE);
                throw new ValidationException(message);
            case warn:
                metricsRecorder.accept(MetricName.warn);
                conditionalLogger.warn(message, LOG_SAMPLING_RATE);
                return Collections.singletonList(message);
            default:
                throw new IllegalStateException(String.format("Unexpected enforcement: %s", enforcement));
        }
    }

    private static String getReferer(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        return site != null ? site.getPage() : "unknown";
    }
}
