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
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.validation.model.ValidationResult;

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
    private static final ConditionalLogger UNRELATED_BID_LOGGER = new ConditionalLogger("not_matched_bid", logger);
    private static final ConditionalLogger SECURE_CREATIVE_LOGGER = new ConditionalLogger("secure_creatives_validation",
            logger);
    private static final ConditionalLogger CREATIVE_SIZE_LOGGER = new ConditionalLogger("creative_size_validation",
            logger);

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};
    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};

    private final BidValidationEnforcement bannerMaxSizeEnforcement;
    private final BidValidationEnforcement secureMarkupEnforcement;
    private final Metrics metrics;

    private final double logSamplingRate;

    public ResponseBidValidator(BidValidationEnforcement bannerMaxSizeEnforcement,
                                BidValidationEnforcement secureMarkupEnforcement,
                                Metrics metrics,
                                double logSamplingRate) {

        this.bannerMaxSizeEnforcement = Objects.requireNonNull(bannerMaxSizeEnforcement);
        this.secureMarkupEnforcement = Objects.requireNonNull(secureMarkupEnforcement);
        this.metrics = Objects.requireNonNull(metrics);

        this.logSamplingRate = logSamplingRate;
    }

    public ValidationResult validate(BidderBid bidderBid,
                                     String bidder,
                                     AuctionContext auctionContext,
                                     BidderAliases aliases) {

        final Bid bid = bidderBid.getBid();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final List<String> warnings = new ArrayList<>();

        try {
            validateCommonFields(bid);
            validateTypeSpecific(bidderBid, bidder);
            validateCurrency(bidderBid.getBidCurrency());

            final Imp correspondingImp = findCorrespondingImp(bid, bidRequest);
            if (bidderBid.getType() == BidType.banner) {
                warnings.addAll(validateBannerFields(bid, bidder, bidRequest, account, correspondingImp, aliases));
            }

            warnings.addAll(validateSecureMarkup(bid, bidder, bidRequest, account, correspondingImp, aliases));
        } catch (ValidationException e) {
            return ValidationResult.error(warnings, e.getMessage());
        }
        return ValidationResult.success(warnings);
    }

    private static void validateCommonFields(Bid bid) throws ValidationException {
        if (bid == null) {
            throw new ValidationException("Empty bid object submitted");
        }

        final String bidId = bid.getId();
        if (StringUtils.isBlank(bidId)) {
            throw new ValidationException("Bid missing required field 'id'");
        }

        if (StringUtils.isBlank(bid.getImpid())) {
            throw new ValidationException("Bid \"%s\" missing required field 'impid'", bidId);
        }

        if (StringUtils.isEmpty(bid.getCrid())) {
            throw new ValidationException("Bid \"%s\" missing creative ID", bidId);
        }
    }

    private void validateTypeSpecific(BidderBid bidderBid, String bidder) throws ValidationException {
        final Bid bid = bidderBid.getBid();
        final boolean isVastSpecificAbsent = bid.getAdm() == null && bid.getNurl() == null;

        if (Objects.equals(bidderBid.getType(), BidType.video) && isVastSpecificAbsent) {
            metrics.updateAdapterRequestErrorMetric(bidder, MetricName.badserverresponse);
            throw new ValidationException("Bid \"%s\" with video type missing adm and nurl", bid.getId());
        }
    }

    private static void validateCurrency(String currency) throws ValidationException {
        try {
            if (StringUtils.isNotBlank(currency)) {
                Currency.getInstance(currency);
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException("BidResponse currency \"%s\" is not valid", currency);
        }
    }

    private Imp findCorrespondingImp(Bid bid, BidRequest bidRequest) throws ValidationException {
        return bidRequest.getImp().stream()
                .filter(imp -> Objects.equals(imp.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> exceptionAndLogOnePercent(
                        "Bid \"%s\" has no corresponding imp in request".formatted(bid.getId())));
    }

    private ValidationException exceptionAndLogOnePercent(String message) {
        UNRELATED_BID_LOGGER.warn(message, logSamplingRate);
        return new ValidationException(message);
    }

    private List<String> validateBannerFields(Bid bid,
                                              String bidder,
                                              BidRequest bidRequest,
                                              Account account,
                                              Imp correspondingImp,
                                              BidderAliases aliases) throws ValidationException {

        final BidValidationEnforcement bannerMaxSizeEnforcement = effectiveBannerMaxSizeEnforcement(account);
        if (bannerMaxSizeEnforcement != BidValidationEnforcement.skip) {
            final Format maxSize = maxSizeForBanner(correspondingImp);

            if (bannerSizeIsNotValid(bid, maxSize)) {
                final String accountId = account.getId();
                final String message = """
                        BidResponse validation `%s`: bidder `%s` response triggers creative \
                        size validation for bid %s, account=%s, referrer=%s, max imp size='%dx%d', \
                        bid response size='%dx%d'""".formatted(
                        bannerMaxSizeEnforcement,
                        bidder,
                        bid.getId(),
                        accountId,
                        getReferer(bidRequest),
                        maxSize.getW(),
                        maxSize.getH(),
                        bid.getW(),
                        bid.getH());

                return singleWarningOrValidationException(
                        bannerMaxSizeEnforcement,
                        metricName -> metrics.updateSizeValidationMetrics(
                                aliases.resolveBidder(bidder), accountId, metricName),
                        CREATIVE_SIZE_LOGGER, message);
            }
        }
        return Collections.emptyList();
    }

    private BidValidationEnforcement effectiveBannerMaxSizeEnforcement(Account account) {
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();
        final AccountBidValidationConfig validationConfig =
                accountAuctionConfig != null ? accountAuctionConfig.getBidValidations() : null;
        final BidValidationEnforcement accountBannerMaxSizeEnforcement =
                validationConfig != null ? validationConfig.getBannerMaxSizeEnforcement() : null;

        return ObjectUtils.defaultIfNull(accountBannerMaxSizeEnforcement, bannerMaxSizeEnforcement);
    }

    private static Format maxSizeForBanner(Imp imp) {
        int maxW = 0;
        int maxH = 0;
        for (final Format size : bannerFormats(imp)) {
            maxW = Math.max(maxW, size.getW());
            maxH = Math.max(maxH, size.getH());
        }
        return Format.builder().w(maxW).h(maxH).build();
    }

    private static List<Format> bannerFormats(Imp imp) {
        final Banner banner = imp.getBanner();
        final List<Format> formats = banner != null ? banner.getFormat() : null;
        return ListUtils.emptyIfNull(formats);
    }

    private static boolean bannerSizeIsNotValid(Bid bid, Format maxSize) {
        final Integer bidW = bid.getW();
        final Integer bidH = bid.getH();
        return bidW == null || bidW > maxSize.getW()
                || bidH == null || bidH > maxSize.getH();
    }

    private List<String> validateSecureMarkup(Bid bid,
                                              String bidder,
                                              BidRequest bidRequest,
                                              Account account,
                                              Imp correspondingImp,
                                              BidderAliases aliases) throws ValidationException {

        if (secureMarkupEnforcement == BidValidationEnforcement.skip) {
            return Collections.emptyList();
        }

        final String accountId = account.getId();
        final String referer = getReferer(bidRequest);
        final String adm = bid.getAdm();

        if (isImpSecure(correspondingImp) && markupIsNotSecure(adm)) {
            final String message = """
                    BidResponse validation `%s`: bidder `%s` response triggers secure \
                    creative validation for bid %s, account=%s, referrer=%s, adm=%s"""
                    .formatted(secureMarkupEnforcement, bidder, bid.getId(), accountId, referer, adm);

            return singleWarningOrValidationException(
                    secureMarkupEnforcement,
                    metricName -> metrics.updateSecureValidationMetrics(
                            aliases.resolveBidder(bidder), accountId, metricName),
                    SECURE_CREATIVE_LOGGER, message);
        }

        return Collections.emptyList();
    }

    private static boolean isImpSecure(Imp imp) {
        return Objects.equals(imp.getSecure(), 1);
    }

    private static boolean markupIsNotSecure(String adm) {
        return StringUtils.containsAny(adm, INSECURE_MARKUP_MARKERS)
                || !StringUtils.containsAny(adm, SECURE_MARKUP_MARKERS);
    }

    private List<String> singleWarningOrValidationException(BidValidationEnforcement enforcement,
                                                            Consumer<MetricName> metricsRecorder,
                                                            ConditionalLogger conditionalLogger,
                                                            String message) throws ValidationException {
        return switch (enforcement) {
            case enforce -> {
                metricsRecorder.accept(MetricName.err);
                conditionalLogger.warn(message, logSamplingRate);
                throw new ValidationException(message);
            }
            case warn -> {
                metricsRecorder.accept(MetricName.warn);
                conditionalLogger.warn(message, logSamplingRate);
                yield Collections.singletonList(message);
            }
            case skip -> throw new IllegalStateException("Unexpected enforcement: " + enforcement);
        };
    }

    private static String getReferer(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        return site != null ? site.getPage() : "unknown";
    }
}
