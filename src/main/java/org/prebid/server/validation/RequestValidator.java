package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A component that validates {@link BidRequest} objects for openrtb2 auction endpoint.
 * Validations are processed by the validate method and returns {@link ValidationResult}.
 */
public class RequestValidator {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(RequestValidator.class));

    private static final String ASTERISK = "*";
    private static final Locale LOCALE = Locale.US;

    private final BidderCatalog bidderCatalog;
    private final ImpValidator impValidator;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final double logSamplingRate;
    private final boolean enabledStrictAppSiteDoohValidation;

    /**
     * Constructs a RequestValidator that will use the BidderParamValidator passed in order to validate all critical
     * properties of bidRequest.
     */
    public RequestValidator(BidderCatalog bidderCatalog,
                            ImpValidator impValidator, Metrics metrics,
                            JacksonMapper mapper,
                            double logSamplingRate,
                            boolean enabledStrictAppSiteDoohValidation) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.impValidator = Objects.requireNonNull(impValidator);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.logSamplingRate = logSamplingRate;
        this.enabledStrictAppSiteDoohValidation = enabledStrictAppSiteDoohValidation;
    }

    /**
     * Validates the {@link BidRequest} against a list of validation checks, however, reports only one problem
     * at a time.
     */
    public ValidationResult validate(BidRequest bidRequest, HttpRequestContext httpRequestContext) {
        final List<String> warnings = new ArrayList<>();
        try {
            if (StringUtils.isBlank(bidRequest.getId())) {
                throw new ValidationException("request missing required field: \"id\"");
            }

            if (bidRequest.getTmax() != null && bidRequest.getTmax() < 0L) {
                throw new ValidationException("request.tmax must be nonnegative. Got " + bidRequest.getTmax());
            }

            validateCur(bidRequest.getCur());

            final ExtRequest extRequest = bidRequest.getExt();

            final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;

            Map<String, String> aliases = Collections.emptyMap();

            if (extRequestPrebid != null) {
                final ExtRequestTargeting targeting = extRequestPrebid.getTargeting();
                if (targeting != null) {
                    validateTargeting(targeting);
                }
                aliases = ObjectUtils.defaultIfNull(extRequestPrebid.getAliases(), Collections.emptyMap());
                validateAliases(aliases);
                validateAliasesGvlIds(extRequestPrebid, aliases);
                validateBidAdjustmentFactors(extRequestPrebid.getBidadjustmentfactors(), aliases);
                validateExtBidPrebidData(extRequestPrebid.getData(), aliases);
                validateSchains(extRequestPrebid.getSchains());
            }

            if (CollectionUtils.isEmpty(bidRequest.getImp())) {
                throw new ValidationException("request.imp must contain at least one element");
            }

            final List<Imp> imps = bidRequest.getImp();
            final List<String> errors = new ArrayList<>();
            final Map<String, Integer> uniqueImps = new HashMap<>();
            for (int i = 0; i < imps.size(); i++) {
                final String impId = imps.get(i).getId();
                if (uniqueImps.get(impId) != null) {
                    errors.add("request.imp[%d].id and request.imp[%d].id are both \"%s\". Imp IDs must be unique."
                            .formatted(uniqueImps.get(impId), i, impId));
                }

                uniqueImps.put(impId, i);
            }

            if (CollectionUtils.isNotEmpty(errors)) {
                throw new ValidationException(String.join(System.lineSeparator(), errors));
            }

            impValidator.validateImps(bidRequest.getImp(), aliases, warnings);

            final List<String> channels = new ArrayList<>();
            Optional.ofNullable(bidRequest.getApp()).ifPresent(ignored -> channels.add("request.app"));
            Optional.ofNullable(bidRequest.getDooh()).ifPresent(ignored -> channels.add("request.dooh"));
            Optional.ofNullable(bidRequest.getSite()).ifPresent(ignored -> channels.add("request.site"));

            final boolean isApp = bidRequest.getApp() != null;
            final boolean isDooh = !isApp && bidRequest.getDooh() != null;
            final boolean isSite = !isApp && !isDooh && bidRequest.getSite() != null;

            if (channels.isEmpty()) {
                throw new ValidationException(
                        "One of request.site or request.app or request.dooh must be defined");
            } else if (channels.size() > 1) {
                if (enabledStrictAppSiteDoohValidation) {
                    metrics.updateAlertsMetrics(MetricName.general);
                    throw new ValidationException(String.join(" and ", channels) + " are present, "
                            + "but no more than one of request.site or request.app or request.dooh can be defined");
                }
                final String logMessage = String.join(" and ", channels) + " are present. "
                        + "Referer: " + httpRequestContext.getHeaders().get(HttpUtil.REFERER_HEADER);
                conditionalLogger.warn(logMessage, logSamplingRate);
            }

            if (isDooh) {
                validateDooh(bidRequest.getDooh());
            }

            if (isSite) {
                validateSite(bidRequest.getSite());
            }

            validateDevice(bidRequest.getDevice());
            validateUser(bidRequest.getUser(), aliases);
            validateRegs(bidRequest.getRegs());
        } catch (ValidationException e) {
            return ValidationResult.error(e.getMessage());
        }
        return ValidationResult.success(warnings);
    }

    /**
     * Validates request.cur field.
     */
    private void validateCur(List<String> currencies) throws ValidationException {
        if (CollectionUtils.isEmpty(currencies)) {
            throw new ValidationException(
                    "currency was not defined either in request.cur or in configuration field adServerCurrency");
        }
    }

    private void validateAliasesGvlIds(ExtRequestPrebid extRequestPrebid,
                                       Map<String, String> aliases) throws ValidationException {

        final Map<String, Integer> aliasGvlIds = MapUtils.emptyIfNull(extRequestPrebid.getAliasgvlids());

        for (Map.Entry<String, Integer> aliasToGvlId : aliasGvlIds.entrySet()) {

            if (!aliases.containsKey(aliasToGvlId.getKey())) {
                throw new ValidationException(
                        "request.ext.prebid.aliasgvlids. vendorId %s refers to unknown bidder alias: %s",
                        aliasToGvlId.getValue(),
                        aliasToGvlId.getKey());
            }

            if (aliasToGvlId.getValue() < 1) {
                throw new ValidationException("""
                        request.ext.prebid.aliasgvlids. Invalid vendorId %s for alias: %s. \
                        Choose a different vendorId, or remove this entry.""",
                        aliasToGvlId.getValue(),
                        aliasToGvlId.getKey());
            }
        }
    }

    private void validateBidAdjustmentFactors(ExtRequestBidAdjustmentFactors adjustmentFactors,
                                              Map<String, String> aliases) throws ValidationException {

        final Map<String, BigDecimal> bidderAdjustments = adjustmentFactors != null
                ? adjustmentFactors.getAdjustments()
                : Collections.emptyMap();

        for (Map.Entry<String, BigDecimal> bidderAdjustment : bidderAdjustments.entrySet()) {
            final String bidder = bidderAdjustment.getKey();

            if (isUnknownBidderOrAlias(bidder, aliases)) {
                throw new ValidationException(
                        "request.ext.prebid.bidadjustmentfactors.%s is not a known bidder or alias", bidder);
            }

            final BigDecimal adjustmentFactor = bidderAdjustment.getValue();
            if (adjustmentFactor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "request.ext.prebid.bidadjustmentfactors.%s must be a positive number. Got %s",
                        bidder, format(adjustmentFactor));
            }
        }
        final Map<ImpMediaType, Map<String, BigDecimal>> adjustmentsMediaTypeFactors =
                adjustmentFactors != null
                        ? adjustmentFactors.getMediatypes()
                        : null;

        if (adjustmentsMediaTypeFactors == null) {
            return;
        }

        for (Map.Entry<ImpMediaType, Map<String, BigDecimal>> entry
                : adjustmentsMediaTypeFactors.entrySet()) {
            validateBidAdjustmentFactorsByMediatype(entry.getKey(), entry.getValue(), aliases);
        }
    }

    private void validateBidAdjustmentFactorsByMediatype(ImpMediaType mediaType,
                                                         Map<String, BigDecimal> bidderAdjustments,
                                                         Map<String, String> aliases) throws ValidationException {

        for (Map.Entry<String, BigDecimal> bidderAdjustment : bidderAdjustments.entrySet()) {
            final String bidder = bidderAdjustment.getKey();

            if (isUnknownBidderOrAlias(bidder, aliases)) {
                throw new ValidationException(
                        "request.ext.prebid.bidadjustmentfactors.%s.%s is not a known bidder or alias",
                        mediaType, bidder);
            }

            final BigDecimal adjustmentFactor = bidderAdjustment.getValue();
            if (adjustmentFactor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "request.ext.prebid.bidadjustmentfactors.%s.%s must be a positive number. Got %s",
                        mediaType, bidder, format(adjustmentFactor));
            }
        }
    }

    private void validateSchains(List<ExtRequestPrebidSchain> schains) throws ValidationException {
        if (schains == null) {
            return;
        }

        final Set<String> schainBidders = new HashSet<>();
        for (final ExtRequestPrebidSchain schain : schains) {
            if (schain == null) {
                continue;
            }

            final List<String> bidders = schain.getBidders();
            if (bidders == null) {
                continue;
            }

            for (final String bidder : bidders) {
                if (schainBidders.contains(bidder)) {
                    throw new ValidationException(
                            "request.ext.prebid.schains contains multiple schains for bidder %s; "
                                    + "it must contain no more than one per bidder.",
                            bidder);
                }

                schainBidders.add(bidder);
            }
        }
    }

    private void validateExtBidPrebidData(ExtRequestPrebidData data, Map<String, String> aliases)
            throws ValidationException {
        if (data != null) {
            validateEidPermissions(data.getEidPermissions(), aliases);
        }
    }

    private void validateEidPermissions(List<ExtRequestPrebidDataEidPermissions> eidPermissions,
                                        Map<String, String> aliases) throws ValidationException {

        if (eidPermissions == null) {
            return;
        }

        for (ExtRequestPrebidDataEidPermissions eidPermission : eidPermissions) {
            if (eidPermission == null) {
                throw new ValidationException("request.ext.prebid.data.eidpermissions[i] can't be null");
            }

            validateEidPermissionSource(eidPermission.getSource());
            validateEidPermissionBidders(eidPermission.getBidders(), aliases);
        }
    }

    private void validateEidPermissionSource(String source) throws ValidationException {
        if (StringUtils.isEmpty(source)) {
            throw new ValidationException("Missing required value request.ext.prebid.data.eidPermissions[].source");
        }
    }

    private void validateEidPermissionBidders(List<String> bidders,
                                              Map<String, String> aliases) throws ValidationException {

        if (CollectionUtils.isEmpty(bidders)) {
            throw new ValidationException("request.ext.prebid.data.eidpermissions[].bidders[] required values"
                    + " but was empty or null");
        }

        for (String bidder : bidders) {
            if (!bidderCatalog.isValidName(bidder) && !bidderCatalog.isValidName(aliases.get(bidder))
                    && ObjectUtils.notEqual(bidder, ASTERISK)) {
                throw new ValidationException(
                        "request.ext.prebid.data.eidPermissions[].bidders[] unrecognized biddercode: '%s'", bidder);
            }
        }
    }

    private boolean isUnknownBidderOrAlias(String bidder, Map<String, String> aliases) {
        return !bidderCatalog.isValidName(bidder) && !aliases.containsKey(bidder);
    }

    private static String format(BigDecimal value) {
        return String.format(LOCALE, "%f", value);
    }

    /**
     * Validates {@link ExtRequestTargeting}.
     */
    private void validateTargeting(ExtRequestTargeting extRequestTargeting) throws ValidationException {
        final JsonNode pricegranularity = extRequestTargeting.getPricegranularity();
        if (pricegranularity != null && !pricegranularity.isNull()) {
            validateExtPriceGranularity(pricegranularity, null);
        }
        validateMediaTypePriceGranularity(extRequestTargeting.getMediatypepricegranularity());

        validateTargetingPrefix(extRequestTargeting);
    }

    private void validateTargetingPrefix(ExtRequestTargeting extRequestTargeting) throws ValidationException {
        final Integer truncateattrchars = extRequestTargeting.getTruncateattrchars();
        final int prefixLength = extRequestTargeting.getPrefix() != null
                ? extRequestTargeting.getPrefix().length()
                : 0;
        final boolean prefixLengthInvalid = truncateattrchars != null
                && prefixLength > 0
                && prefixLength + 11 > truncateattrchars; // 11 - length of the longest targeting keyword without prefix
        if (prefixLengthInvalid) {
            throw new ValidationException("ext.prebid.targeting: decrease prefix length or increase truncateattrchars"
                    + " by " + (prefixLength + 11 - truncateattrchars) + " characters");
        }
    }

    /**
     * Validates {@link ExtPriceGranularity}.
     */
    private void validateExtPriceGranularity(JsonNode priceGranularity, BidType type)
            throws ValidationException {
        final ExtPriceGranularity extPriceGranularity;
        try {
            extPriceGranularity = mapper.mapper().treeToValue(priceGranularity, ExtPriceGranularity.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Error while parsing request.ext.prebid.targeting.%s"
                    .formatted(type == null ? "pricegranularity" : "mediatypepricegranularity." + type));
        }

        final Integer precision = extPriceGranularity.getPrecision();
        if (precision != null && precision < 0) {
            throw new ValidationException("%srice granularity error: precision must be non-negative"
                    .formatted(type == null ? "P" : StringUtils.capitalize(type.name()) + " p"));
        }
        validateGranularityRanges(extPriceGranularity.getRanges());
    }

    /**
     * Validates {@link ExtMediaTypePriceGranularity} if it's present.
     */
    private void validateMediaTypePriceGranularity(ExtMediaTypePriceGranularity mediaTypePriceGranularity)
            throws ValidationException {
        if (mediaTypePriceGranularity != null) {
            final ObjectNode banner = mediaTypePriceGranularity.getBanner();
            final ObjectNode video = mediaTypePriceGranularity.getVideo();
            final ObjectNode xNative = mediaTypePriceGranularity.getXNative();
            final boolean isBannerNull = banner == null || banner.isNull();
            final boolean isVideoNull = video == null || video.isNull();
            final boolean isNativeNull = xNative == null || xNative.isNull();
            if (isBannerNull && isVideoNull && isNativeNull) {
                throw new ValidationException(
                        "Media type price granularity error: must have at least one media type present");
            }
            if (!isBannerNull) {
                validateExtPriceGranularity(banner, BidType.banner);
            }
            if (!isVideoNull) {
                validateExtPriceGranularity(video, BidType.video);
            }
            if (!isNativeNull) {
                validateExtPriceGranularity(xNative, BidType.xNative);
            }
        }
    }

    /**
     * Validates list of {@link ExtRequestTargeting}s as set of ranges.
     */
    private static void validateGranularityRanges(List<ExtGranularityRange> ranges) throws ValidationException {
        if (CollectionUtils.isEmpty(ranges)) {
            throw new ValidationException("Price granularity error: empty granularity definition supplied");
        }

        BigDecimal previousRangeMax = null;
        for (ExtGranularityRange range : ranges) {
            final BigDecimal rangeMax = range.getMax();

            validateGranularityRangeMax(rangeMax);
            validateGranularityRangeIncrement(range);
            validateGranularityRangeMaxOrdering(previousRangeMax, rangeMax);

            previousRangeMax = rangeMax;
        }
    }

    private static void validateGranularityRangeMax(BigDecimal rangeMax)
            throws ValidationException {
        if (rangeMax == null) {
            throw new ValidationException("Price granularity error: max value should not be missed");
        }
    }

    private static void validateGranularityRangeMaxOrdering(BigDecimal previousRangeMax, BigDecimal rangeMax)
            throws ValidationException {
        if (previousRangeMax != null && previousRangeMax.compareTo(rangeMax) > 0) {
            throw new ValidationException(
                    "Price granularity error: range list must be ordered with increasing \"max\"");
        }
    }

    private static void validateGranularityRangeIncrement(ExtGranularityRange range)
            throws ValidationException {
        final BigDecimal increment = range.getIncrement();
        if (increment == null || increment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Price granularity error: increment must be a nonzero positive number");
        }
    }

    /**
     * Validates aliases. Throws {@link ValidationException} in cases when alias points to invalid bidder or when alias
     * is equals to itself.
     */
    private void validateAliases(Map<String, String> aliases) throws ValidationException {
        for (final Map.Entry<String, String> aliasToBidder : aliases.entrySet()) {
            final String alias = aliasToBidder.getKey();
            final String coreBidder = aliasToBidder.getValue();
            if (!bidderCatalog.isValidName(coreBidder)) {
                throw new ValidationException(
                        "request.ext.prebid.aliases.%s refers to unknown bidder: %s".formatted(alias, coreBidder));
            }
            if (!bidderCatalog.isActive(coreBidder)) {
                throw new ValidationException(
                        "request.ext.prebid.aliases.%s refers to disabled bidder: %s".formatted(alias, coreBidder));
            }
            if (alias.equals(coreBidder)) {
                throw new ValidationException("""
                        request.ext.prebid.aliases.%s defines a no-op alias. \
                        Choose a different alias, or remove this entry""".formatted(alias));
            }
        }
    }

    private void validateSite(Site site) throws ValidationException {
        if (site != null) {
            if (StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
                throw new ValidationException(
                        "request.site should include at least one of request.site.id or request.site.page");
            }

            final ExtSite siteExt = site.getExt();
            if (siteExt != null) {
                final Integer amp = siteExt.getAmp();
                if (amp != null && (amp < 0 || amp > 1)) {
                    throw new ValidationException("request.site.ext.amp must be either 1, 0, or undefined");
                }
            }
        }
    }

    private void validateDooh(Dooh dooh) throws ValidationException {
        if (dooh.getId() == null && CollectionUtils.isEmpty(dooh.getVenuetype())) {
            throw new ValidationException(
                    "request.dooh should include at least one of request.dooh.id or request.dooh.venuetype.");
        }
    }

    private void validateDevice(Device device) throws ValidationException {
        final ExtDevice extDevice = device != null ? device.getExt() : null;
        if (extDevice != null) {
            final ExtDevicePrebid extDevicePrebid = extDevice.getPrebid();
            final ExtDeviceInt interstitial = extDevicePrebid != null ? extDevicePrebid.getInterstitial() : null;
            if (interstitial != null) {
                validateInterstitial(interstitial);
            }
        }
    }

    private void validateInterstitial(ExtDeviceInt interstitial) throws ValidationException {
        final Integer minWidthPerc = interstitial.getMinWidthPerc();
        if (minWidthPerc == null || minWidthPerc < 0 || minWidthPerc > 100) {
            throw new ValidationException(
                    "request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
        }
        final Integer minHeightPerc = interstitial.getMinHeightPerc();
        if (minHeightPerc == null || minHeightPerc < 0 || minHeightPerc > 100) {
            throw new ValidationException(
                    "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
        }
    }

    private void validateUser(User user, Map<String, String> aliases) throws ValidationException {
        if (user != null) {
            validateUserExt(user.getExt(), aliases);

            final List<Eid> eids = user.getEids();
            if (eids != null) {
                for (int index = 0; index < eids.size(); index++) {
                    final Eid eid = eids.get(index);
                    if (StringUtils.isBlank(eid.getSource())) {
                        throw new ValidationException(
                                "request.user.eids[%d] missing required field: \"source\"", index);
                    }
                    final List<Uid> eidUids = eid.getUids();
                    if (CollectionUtils.isEmpty(eidUids)) {
                        throw new ValidationException(
                                "request.user.eids[%d].uids must contain at least one element", index);
                    }
                    for (int uidsIndex = 0; uidsIndex < eidUids.size(); uidsIndex++) {
                        final Uid uid = eidUids.get(uidsIndex);
                        if (StringUtils.isBlank(uid.getId())) {
                            throw new ValidationException(
                                    "request.user.eids[%d].uids[%d] missing required field: \"id\"", index,
                                    uidsIndex);
                        }
                    }
                }
            }
        }
    }

    private void validateUserExt(ExtUser extUser, Map<String, String> aliases) throws ValidationException {
        if (extUser != null) {
            final ExtUserPrebid prebid = extUser.getPrebid();
            if (prebid != null) {
                final Map<String, String> buyerUids = prebid.getBuyeruids();
                if (MapUtils.isEmpty(buyerUids)) {
                    throw new ValidationException("request.user.ext.prebid requires a \"buyeruids\" property "
                            + "with at least one ID defined. If none exist, then request.user.ext.prebid"
                            + " should not be defined");
                }

                for (String bidder : buyerUids.keySet()) {
                    if (isUnknownBidderOrAlias(bidder, aliases)) {
                        throw new ValidationException("request.user.ext.%s is neither a known bidder "
                                + "name nor an alias in request.ext.prebid.aliases", bidder);
                    }
                }
            }
        }
    }

    /**
     * Validates {@link Regs}. Throws {@link ValidationException} in case if
     * its gdpr value has different value to 0 or 1.
     */
    private void validateRegs(Regs regs) throws ValidationException {
        final Integer gdpr = regs != null ? regs.getGdpr() : null;
        if (gdpr != null && gdpr != 0 && gdpr != 1) {
            throw new ValidationException("request.regs.ext.gdpr must be either 0 or 1");
        }
    }

}
