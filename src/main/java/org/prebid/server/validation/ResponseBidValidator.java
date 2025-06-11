package org.prebid.server.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Validator for response {@link Bid} object.
 */
public class ResponseBidValidator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseBidValidator.class);
    private static final ConditionalLogger unrelatedBidLogger =
            new ConditionalLogger("not_matched_bid", logger);
    private static final ConditionalLogger secureCreativeLogger =
            new ConditionalLogger("secure_creatives_validation", logger);
    private static final ConditionalLogger creativeSizeLogger =
            new ConditionalLogger("creative_size_validation", logger);
    private static final ConditionalLogger adPoddingLogger =
            new ConditionalLogger("ad_podding_validation", logger);
    private static final ConditionalLogger alternateBidderCodeLogger =
            new ConditionalLogger("alternate_bidder_code_validation", logger);

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};
    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};

    private final BidValidationEnforcement bannerMaxSizeEnforcement;
    private final BidValidationEnforcement secureMarkupEnforcement;
    private final BidValidationEnforcement adPoddingEnforcement;
    private final CurrencyConversionService currencyConversionService;
    private final Metrics metrics;
    private final ObjectMapper mapper;

    private final double logSamplingRate;

    public ResponseBidValidator(BidValidationEnforcement bannerMaxSizeEnforcement,
                                BidValidationEnforcement secureMarkupEnforcement,
                                BidValidationEnforcement adPoddingEnforcement,
                                CurrencyConversionService currencyConversionService,
                                Metrics metrics,
                                JacksonMapper mapper,
                                double logSamplingRate) {

        this.bannerMaxSizeEnforcement = Objects.requireNonNull(bannerMaxSizeEnforcement);
        this.secureMarkupEnforcement = Objects.requireNonNull(secureMarkupEnforcement);
        this.adPoddingEnforcement = Objects.requireNonNull(adPoddingEnforcement);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper).mapper();

        this.logSamplingRate = logSamplingRate;
    }

    public ValidationResult validate(BidderBid bidderBid,
                                     String bidder,
                                     AuctionContext auctionContext,
                                     BidderAliases aliases) {

        final Bid bid = bidderBid.getBid();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();
        final BidRejectionTracker bidRejectionTracker = auctionContext.getBidRejectionTrackers().get(bidder);
        final List<String> warnings = new ArrayList<>();

        try {
            validateCommonFields(bid);
            validateTypeSpecific(bidderBid, bidder);
            validateCurrency(bidderBid.getBidCurrency());
            validateSeat(bidderBid, bidder, account, bidRejectionTracker, aliases);

            final Imp correspondingImp = findCorrespondingImp(bid, bidRequest);
            if (bidderBid.getType() == BidType.banner) {
                warnings.addAll(validateBannerFields(
                        bidderBid,
                        bidder,
                        bidRequest,
                        account,
                        correspondingImp,
                        aliases,
                        bidRejectionTracker));
            }

            if (bidderBid.getType() == BidType.video || bidderBid.getType() == BidType.audio) {
                warnings.addAll(validateAdPoddingFields(
                        bidderBid,
                        bidder,
                        bidRequest,
                        account,
                        correspondingImp,
                        aliases,
                        bidRejectionTracker));
            }

            warnings.addAll(validateSecureMarkup(
                    bidderBid,
                    bidder,
                    bidRequest,
                    account,
                    correspondingImp,
                    aliases,
                    bidRejectionTracker));

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

    private void validateSeat(BidderBid bid,
                              String bidder,
                              Account account,
                              BidRejectionTracker bidRejectionTracker,
                              BidderAliases bidderAliases) throws ValidationException {

        final String seat = bid.getSeat();
        if (seat != null
                && !StringUtils.equalsIgnoreCase(bidder, seat)
                && !bidderAliases.isAllowedAlternateBidderCode(bidder, seat)) {

            final String message = "invalid bidder code %s was set by the adapter %s for the account %s"
                    .formatted(bid.getSeat(), bidder, account.getId());
            bidRejectionTracker.rejectBid(bid, BidRejectionReason.RESPONSE_REJECTED_GENERAL);
            metrics.updateSeatValidationMetrics(bidder);
            alternateBidderCodeLogger.warn(message, logSamplingRate);
            throw new ValidationException(message);
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
        unrelatedBidLogger.warn(message, logSamplingRate);
        return new ValidationException(message);
    }

    private List<String> validateBannerFields(BidderBid bidderBid,
                                              String bidder,
                                              BidRequest bidRequest,
                                              Account account,
                                              Imp correspondingImp,
                                              BidderAliases aliases,
                                              BidRejectionTracker bidRejectionTracker) throws ValidationException {

        final BidValidationEnforcement bannerMaxSizeEnforcement = effectiveBannerMaxSizeEnforcement(account);
        if (bannerMaxSizeEnforcement != BidValidationEnforcement.skip) {
            final Format maxSize = maxSizeForBanner(correspondingImp);
            final Bid bid = bidderBid.getBid();
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
                        creativeSizeLogger,
                        message,
                        bidRejectionTracker,
                        bidderBid,
                        BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED);
            }
        }
        return Collections.emptyList();
    }

    private BidValidationEnforcement effectiveBannerMaxSizeEnforcement(Account account) {
        return Optional.ofNullable(account.getAuction())
                .map(AccountAuctionConfig::getBidValidations)
                .map(AccountBidValidationConfig::getBannerMaxSizeEnforcement)
                .orElse(bannerMaxSizeEnforcement);
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

    private List<String> validateSecureMarkup(BidderBid bidderBid,
                                              String bidder,
                                              BidRequest bidRequest,
                                              Account account,
                                              Imp correspondingImp,
                                              BidderAliases aliases,
                                              BidRejectionTracker bidRejectionTracker) throws ValidationException {

        if (secureMarkupEnforcement == BidValidationEnforcement.skip) {
            return Collections.emptyList();
        }

        final String accountId = account.getId();
        final String referer = getReferer(bidRequest);
        final Bid bid = bidderBid.getBid();
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
                    secureCreativeLogger,
                    message,
                    bidRejectionTracker,
                    bidderBid,
                    BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE);
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

    private List<String> validateAdPoddingFields(BidderBid bidderBid,
                                                 String bidder,
                                                 BidRequest bidRequest,
                                                 Account account,
                                                 Imp correspondingImp,
                                                 BidderAliases aliases,
                                                 BidRejectionTracker bidRejectionTracker) throws ValidationException {

        final BidValidationEnforcement adPoddingEnforcement = effectiveAdPoddingEnforcement(account);
        if (adPoddingEnforcement == BidValidationEnforcement.skip) {
            return Collections.emptyList();
        }

        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();

        if (isNotValidVideo(bid, correspondingImp.getVideo(), bidRequest, bidCurrency)
                || isNotValidAudio(bid, correspondingImp.getAudio(), bidRequest, bidCurrency)) {

            final String accountId = account.getId();
            final String message = """
                    BidResponse validation `%s`: bidder `%s` response triggers ad podding \
                    validation for bid %s, account=%s, referrer=%s""".formatted(
                    adPoddingEnforcement,
                    bidder,
                    bid.getId(),
                    accountId,
                    getReferer(bidRequest));

            return singleWarningOrValidationException(
                    adPoddingEnforcement,
                    metricName -> metrics.updateAdPoddingValidationMetrics(
                            aliases.resolveBidder(bidder), accountId, metricName),
                    adPoddingLogger,
                    message,
                    bidRejectionTracker,
                    bidderBid,
                    //todo: clarify the code
                    BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
        }
        return Collections.emptyList();
    }

    private boolean isNotValidVideo(Bid bid, Video video, BidRequest bidRequest, String bidCurrency) {
        return video != null
                && video.getPodid() != null
                && isNotValidAdPoddingFields(bid, video.getRqddurs(), video.getMinduration(), video.getMaxduration(),
                video.getMincpmpersec(), bidRequest, bidCurrency);
    }

    private boolean isNotValidAudio(Bid bid, Audio audio, BidRequest bidRequest, String bidCurrency) {
        return audio != null
                && audio.getPodid() != null
                && isNotValidAdPoddingFields(bid, audio.getRqddurs(), audio.getMinduration(), audio.getMaxduration(),
                audio.getMincpmpersec(), bidRequest, bidCurrency);
    }

    private boolean isNotValidAdPoddingFields(Bid bid,
                                              List<Integer> requiredDurations,
                                              Integer minDuration,
                                              Integer maxDuration,
                                              BigDecimal mincpmpersec,
                                              BidRequest bidRequest,
                                              String bidCurrency) {

        final Integer duration = ObjectUtils.firstNonNull(bid.getDur(), getBidMetaDuration(bid), 0);
        final Integer highestDurationBucket = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getTargeting)
                .map(ExtRequestTargeting::getDurationrangesec)
                .filter(CollectionUtils::isNotEmpty)
                .map(List::getLast)
                .orElse(null);

        return duration <= 0
                || (minDuration != null && duration < minDuration)
                || (maxDuration != null && duration > maxDuration)
                || (requiredDurations != null && !requiredDurations.contains(duration))
                || (highestDurationBucket != null && duration > highestDurationBucket)
                || isMinCpmLessThanBidPrice(bid, mincpmpersec, bidRequest, bidCurrency, duration);
    }

    private boolean isMinCpmLessThanBidPrice(Bid bid,
                                             BigDecimal mincpmpersec,
                                             BidRequest bidRequest,
                                             String bidCurrency,
                                             Integer duration) {
        if (mincpmpersec == null) {
            return false;
        }

        final BigDecimal minCpm = mincpmpersec.multiply(new BigDecimal(duration));
        final String requestCurrency = bidRequest.getCur().getFirst();
        final BigDecimal convertedMinCpm = currencyConversionService.convertCurrency(
                minCpm,
                bidRequest,
                requestCurrency,
                bidCurrency);

        return convertedMinCpm.compareTo(bid.getPrice()) < 0;
    }

    private Integer getBidMetaDuration(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .filter(ext -> ext.hasNonNull("prebid"))
                .map(this::convertValue)
                .map(ExtBidPrebid::getMeta)
                .map(ExtBidPrebidMeta::getDur)
                .orElse(null);
    }

    private ExtBidPrebid convertValue(JsonNode jsonNode) {
        try {
            return mapper.convertValue(jsonNode.get("prebid"), ExtBidPrebid.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BidValidationEnforcement effectiveAdPoddingEnforcement(Account account) {
        return Optional.ofNullable(account.getAuction())
                .map(AccountAuctionConfig::getBidValidations)
                .map(AccountBidValidationConfig::getAdPoddingEnforcement)
                .orElse(adPoddingEnforcement);
    }

    private List<String> singleWarningOrValidationException(
            BidValidationEnforcement enforcement,
            Consumer<MetricName> metricsRecorder,
            ConditionalLogger conditionalLogger,
            String message,
            BidRejectionTracker bidRejectionTracker,
            BidderBid bidderBid,
            BidRejectionReason bidRejectionReason) throws ValidationException {

        return switch (enforcement) {
            case enforce -> {
                bidRejectionTracker.rejectBid(bidderBid, bidRejectionReason);
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
