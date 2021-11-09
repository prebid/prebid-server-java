package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.util.DealUtil;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final double LOG_SAMPLING_RATE = 0.01;

    private static final String[] INSECURE_MARKUP_MARKERS = {"http:", "http%3A"};
    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";

    private static final String DEALS_ONLY = "dealsonly";

    private final BidValidationEnforcement bannerMaxSizeEnforcement;
    private final BidValidationEnforcement secureMarkupEnforcement;
    private final Metrics metrics;

    private final JacksonMapper mapper;
    private final boolean dealsEnabled;

    public ResponseBidValidator(BidValidationEnforcement bannerMaxSizeEnforcement,
                                BidValidationEnforcement secureMarkupEnforcement,
                                Metrics metrics,
                                JacksonMapper mapper,
                                boolean dealsEnabled) {

        this.bannerMaxSizeEnforcement = Objects.requireNonNull(bannerMaxSizeEnforcement);
        this.secureMarkupEnforcement = Objects.requireNonNull(secureMarkupEnforcement);
        this.metrics = Objects.requireNonNull(metrics);

        this.mapper = Objects.requireNonNull(mapper);
        this.dealsEnabled = dealsEnabled;
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

            if (dealsEnabled) {
                validateDealsFor(bidderBid, auctionContext.getBidRequest(), bidder, aliases, warnings);
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

    private static Imp findCorrespondingImp(Bid bid, BidRequest bidRequest) throws ValidationException {
        return bidRequest.getImp().stream()
                .filter(imp -> Objects.equals(imp.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> exceptionAndLogOnePercent(
                        String.format("Bid \"%s\" has no corresponding imp in request", bid.getId())));
    }

    private static ValidationException exceptionAndLogOnePercent(String message) {
        UNRELATED_BID_LOGGER.warn(message, 0.01);
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
                final String message = String.format(
                        "BidResponse validation `%s`: bidder `%s` response triggers creative size validation for bid "
                                + "%s, account=%s, referrer=%s, max imp size='%dx%d', bid response size='%dx%d'",
                        bannerMaxSizeEnforcement, bidder, bid.getId(), accountId, getReferer(bidRequest),
                        maxSize.getW(), maxSize.getH(), bid.getW(), bid.getH());

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
            final String message = String.format("BidResponse validation `%s`: bidder `%s` response triggers secure"
                            + " creative validation for bid %s, account=%s, referrer=%s, adm=%s",
                    secureMarkupEnforcement, bidder, bid.getId(), accountId, referer, adm);
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

    private void validateDealsFor(BidderBid bidderBid,
                                  BidRequest bidRequest,
                                  String bidder,
                                  BidderAliases aliases,
                                  List<String> warnings) throws ValidationException {

        final Bid bid = bidderBid.getBid();
        final String bidId = bid.getId();

        final Imp imp = bidRequest.getImp().stream()
                .filter(curImp -> Objects.equals(curImp.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Bid \"%s\" has no corresponding imp in request", bidId));

        final String dealId = bid.getDealid();

        if (isDealsOnlyImp(imp, bidder) && dealId == null) {
            throw new ValidationException("Bid \"%s\" missing required field 'dealid'", bidId);
        }

        if (dealId != null) {
            final Set<String> dealIdsFromImp = getDealIdsFromImp(imp, bidder, aliases);
            if (CollectionUtils.isNotEmpty(dealIdsFromImp) && !dealIdsFromImp.contains(dealId)) {
                warnings.add(String.format("WARNING: Bid \"%s\" has 'dealid' not present in corresponding imp in"
                                + " request. 'dealid' in bid: '%s', deal Ids in imp: '%s'",
                        bidId, dealId, String.join(",", dealIdsFromImp)));
            }
            if (bidderBid.getType() == BidType.banner) {
                if (imp.getBanner() == null) {
                    throw new ValidationException("Bid \"%s\" has banner media type but corresponding imp in request "
                            + "is missing 'banner' object", bidId);
                }

                final List<Format> bannerFormats = getBannerFormats(imp);
                if (bidSizeNotInFormats(bid, bannerFormats)) {
                    throw new ValidationException("Bid \"%s\" has 'w' and 'h' not supported by corresponding imp in "
                            + "request. Bid dimensions: '%dx%d', formats in imp: '%s'", bidId, bid.getW(), bid.getH(),
                            formatSizes(bannerFormats));
                }

                if (isPgDeal(imp, dealId)) {
                    validateIsInLineItemSizes(bid, bidId, imp);
                }
            }
        }
    }

    private void validateIsInLineItemSizes(Bid bid, String bidId, Imp imp) throws ValidationException {
        final List<Format> lineItemSizes = getLineItemSizes(imp);
        if (bidSizeNotInFormats(bid, lineItemSizes)) {
            throw new ValidationException("Bid \"%s\" has 'w' and 'h' not matched to Line Item. Bid "
                    + "dimensions: '%dx%d', Line Item sizes: '%s'", bidId, bid.getW(), bid.getH(),
                    formatSizes(lineItemSizes));
        }
    }

    private static boolean isDealsOnlyImp(Imp imp, String bidder) {
        final JsonNode dealsOnlyNode = bidderParamsFromImp(imp).path(bidder).path(DEALS_ONLY);
        return dealsOnlyNode.isBoolean() && dealsOnlyNode.asBoolean();
    }

    private static JsonNode bidderParamsFromImp(Imp imp) {
        return imp.getExt().path(PREBID_EXT).path(BIDDER_EXT);
    }

    private Set<String> getDealIdsFromImp(Imp imp, String bidder, BidderAliases aliases) {
        return getDeals(imp)
                .filter(Objects::nonNull)
                .filter(deal -> DealUtil.isBidderHasDeal(bidder, dealExt(deal.getExt()), aliases))
                .map(Deal::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Stream<Deal> getDeals(Imp imp) {
        final Pmp pmp = imp.getPmp();
        return pmp != null ? pmp.getDeals().stream() : Stream.empty();
    }

    private static boolean bidSizeNotInFormats(Bid bid, List<Format> formats) {
        return formats.stream()
                .noneMatch(format -> sizesEqual(bid, format));
    }

    private static boolean sizesEqual(Bid bid, Format format) {
        return Objects.equals(format.getH(), bid.getH()) && Objects.equals(format.getW(), bid.getW());
    }

    private static List<Format> getBannerFormats(Imp imp) {
        return ListUtils.emptyIfNull(imp.getBanner().getFormat());
    }

    private List<Format> getLineItemSizes(Imp imp) {
        return getDeals(imp)
                .map(Deal::getExt)
                .filter(Objects::nonNull)
                .map(this::dealExt)
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .filter(Objects::nonNull)
                .map(ExtDealLine::getSizes)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean isPgDeal(Imp imp, String dealId) {
        return getDeals(imp)
                .filter(Objects::nonNull)
                .filter(deal -> Objects.equals(deal.getId(), dealId))
                .map(Deal::getExt)
                .filter(Objects::nonNull)
                .map(this::dealExt)
                .filter(Objects::nonNull)
                .map(ExtDeal::getLine)
                .filter(Objects::nonNull)
                .map(ExtDealLine::getLineItemId)
                .anyMatch(Objects::nonNull);
    }

    private ExtDeal dealExt(JsonNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, ExtDeal.class);
        } catch (JsonProcessingException e) {
            logger.warn("Error decoding deal.ext: {0}", e, e.getMessage());
            return null;
        }
    }

    private static String formatSizes(List<Format> lineItemSizes) {
        return lineItemSizes.stream()
                .map(format -> String.format("%dx%d", format.getW(), format.getH()))
                .collect(Collectors.joining(","));
    }
}
