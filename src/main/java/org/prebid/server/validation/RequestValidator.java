package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import com.iab.openrtb.request.ntv.ContextSubType;
import com.iab.openrtb.request.ntv.ContextType;
import com.iab.openrtb.request.ntv.DataAssetType;
import com.iab.openrtb.request.ntv.EventTrackingMethod;
import com.iab.openrtb.request.ntv.EventType;
import com.iab.openrtb.request.ntv.PlacementType;
import com.iab.openrtb.request.ntv.Protocol;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredBidResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A component that validates {@link BidRequest} objects for openrtb2 auction endpoint.
 * Validations are processed by the validate method and returns {@link ValidationResult}.
 */
public class RequestValidator {

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String ASTERISK = "*";
    private static final Locale LOCALE = Locale.US;
    private static final Integer NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND = 500;

    private static final String DOCUMENTATION = "https://iabtechlab.com/wp-content/uploads/2016/07/"
            + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf";

    private final BidderCatalog bidderCatalog;
    private final BidderParamValidator bidderParamValidator;
    private final JacksonMapper mapper;

    /**
     * Constructs a RequestValidator that will use the BidderParamValidator passed in order to validate all critical
     * properties of bidRequest.
     */
    public RequestValidator(BidderCatalog bidderCatalog,
                            BidderParamValidator bidderParamValidator,
                            JacksonMapper mapper) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Validates the {@link BidRequest} against a list of validation checks, however, reports only one problem
     * at a time.
     */
    public ValidationResult validate(BidRequest bidRequest) {
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

            for (int index = 0; index < bidRequest.getImp().size(); index++) {
                validateImp(bidRequest.getImp().get(index), aliases, index, warnings);
            }

            if (bidRequest.getSite() == null && bidRequest.getApp() == null) {
                throw new ValidationException("request.site or request.app must be defined");
            }

            // if site and app present site will be removed
            if (bidRequest.getApp() == null) {
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
        if (eidPermissions != null) {
            final Set<String> uniqueEidsSources = new HashSet<>();
            for (ExtRequestPrebidDataEidPermissions eidPermission : eidPermissions) {
                validateEidPermission(eidPermission, aliases, uniqueEidsSources);
            }
        }
    }

    private void validateEidPermission(ExtRequestPrebidDataEidPermissions eidPermission,
                                       Map<String, String> aliases,
                                       Set<String> uniqueEidsSources)
            throws ValidationException {
        if (eidPermission == null) {
            throw new ValidationException("request.ext.prebid.data.eidpermissions[] can't be null");
        }
        final String eidPermissionSource = eidPermission.getSource();

        validateEidPermissionSource(eidPermissionSource);
        validateDuplicatedSources(uniqueEidsSources, eidPermissionSource);
        validateEidPermissionBidders(eidPermission.getBidders(), aliases);
    }

    private void validateEidPermissionSource(String source) throws ValidationException {
        if (StringUtils.isEmpty(source)) {
            throw new ValidationException("Missing required value request.ext.prebid.data.eidPermissions[].source");
        }
    }

    private void validateDuplicatedSources(Set<String> uniqueEidsSources, String eidSource) throws ValidationException {
        if (uniqueEidsSources.contains(eidSource)) {
            throw new ValidationException("Duplicate source %s in request.ext.prebid.data.eidpermissions[]"
                    .formatted(eidSource));
        }
        uniqueEidsSources.add(eidSource);
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

        final Boolean includeWinners = extRequestTargeting.getIncludewinners();
        final Boolean includeBidderKeys = extRequestTargeting.getIncludebidderkeys();
        if (Objects.equals(includeWinners, false) && Objects.equals(includeBidderKeys, false)) {
            throw new ValidationException("ext.prebid.targeting: At least one of includewinners or includebidderkeys"
                    + " must be enabled to enable targeting support");
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
                final Set<String> uniqueSources = eids.stream()
                        .map(Eid::getSource)
                        .collect(Collectors.toSet());
                if (eids.size() != uniqueSources.size()) {
                    throw new ValidationException("request.user.eids must contain unique sources");
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

    private void validateImp(Imp imp, Map<String, String> aliases, int index, List<String> warnings)
            throws ValidationException {
        if (StringUtils.isBlank(imp.getId())) {
            throw new ValidationException("request.imp[%d] missing required field: \"id\"", index);
        }
        if (imp.getMetric() != null && !imp.getMetric().isEmpty()) {
            validateMetrics(imp.getMetric(), index);
        }
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getAudio() == null && imp.getXNative() == null) {
            throw new ValidationException(
                    "request.imp[%d] must contain at least one of \"banner\", \"video\", \"audio\", or \"native\"",
                    index);
        }

        final boolean isInterstitialImp = Objects.equals(imp.getInstl(), 1);
        validateBanner(imp.getBanner(), isInterstitialImp, index);
        validateVideoMimes(imp.getVideo(), index);
        validateAudioMimes(imp.getAudio(), index);
        fillAndValidateNative(imp.getXNative(), index);
        validatePmp(imp.getPmp(), index);
        validateImpExt(imp.getExt(), aliases, index, warnings);
    }

    private void fillAndValidateNative(Native xNative, int impIndex) throws ValidationException {
        if (xNative == null) {
            return;
        }

        final Request nativeRequest = parseNativeRequest(xNative.getRequest(), impIndex);

        validateNativeContextTypes(nativeRequest.getContext(), nativeRequest.getContextsubtype(), impIndex);
        validateNativePlacementType(nativeRequest.getPlcmttype(), impIndex);
        final List<Asset> updatedAssets = validateAndGetUpdatedNativeAssets(nativeRequest.getAssets(), impIndex);
        validateNativeEventTrackers(nativeRequest.getEventtrackers(), impIndex);

        // modifier was added to reduce memory consumption on updating bidRequest.imp[i].native.request object
        xNative.setRequest(toEncodedRequest(nativeRequest, updatedAssets));
    }

    private Request parseNativeRequest(String rawStringNativeRequest, int impIndex) throws ValidationException {
        if (StringUtils.isBlank(rawStringNativeRequest)) {
            throw new ValidationException("request.imp[%d].native contains empty request value", impIndex);
        }
        try {
            return mapper.mapper().readValue(rawStringNativeRequest, Request.class);
        } catch (IOException e) {
            throw new ValidationException("Error while parsing request.imp[%d].native.request: %s",
                    impIndex,
                    ExceptionUtils.getMessage(e));
        }
    }

    private void validateNativeContextTypes(Integer context, Integer contextSubType, int index)
            throws ValidationException {

        final int type = context != null ? context : 0;
        if (type == 0) {
            return;
        }

        if (type < ContextType.CONTENT.getValue()
                || (type > ContextType.PRODUCT.getValue() && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.context is invalid. See " + documentationOnPage(39), index);
        }

        final int subType = contextSubType != null ? contextSubType : 0;
        if (subType < 0) {
            throw new ValidationException(
                    "request.imp[%d].native.request.contextsubtype is invalid. See " + documentationOnPage(39), index);
        }

        if (subType == 0) {
            return;
        }

        if (subType >= ContextSubType.GENERAL.getValue() && subType <= ContextSubType.USER_GENERATED.getValue()) {
            if (type != ContextType.CONTENT.getValue() && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND) {
                throw new ValidationException(
                        "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                                + "combination. See " + documentationOnPage(39), index, context, contextSubType);
            }
            return;
        }

        if (subType >= ContextSubType.SOCIAL.getValue() && subType <= ContextSubType.CHAT.getValue()) {
            if (type != ContextType.SOCIAL.getValue() && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND) {
                throw new ValidationException(
                        "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                                + "combination. See " + documentationOnPage(39), index, context, contextSubType);
            }
            return;
        }

        if (subType >= ContextSubType.SELLING.getValue() && subType <= ContextSubType.PRODUCT_REVIEW.getValue()) {
            if (type != ContextType.PRODUCT.getValue() && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND) {
                throw new ValidationException(
                        "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                                + "combination. See " + documentationOnPage(39), index, context, contextSubType);
            }
            return;
        }

        if (subType < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND) {
            throw new ValidationException(
                    "request.imp[%d].native.request.contextsubtype is invalid. See " + documentationOnPage(39), index);
        }
    }

    private void validateNativePlacementType(Integer placementType, int index) throws ValidationException {
        final int type = placementType != null ? placementType : 0;
        if (type == 0) {
            return;
        }

        if (type < PlacementType.FEED.getValue() || (type > PlacementType.RECOMMENDATION_WIDGET.getValue()
                && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.plcmttype is invalid. See " + documentationOnPage(40), index, type);
        }
    }

    private List<Asset> validateAndGetUpdatedNativeAssets(List<Asset> assets, int impIndex) throws ValidationException {
        if (CollectionUtils.isEmpty(assets)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets must be an array containing at least one object", impIndex);
        }

        final List<Asset> updatedAssets = new ArrayList<>();
        for (int i = 0; i < assets.size(); i++) {
            final Asset asset = assets.get(i);
            validateNativeAsset(asset, impIndex, i);

            final Asset updatedAsset = asset.getId() != null ? asset : asset.toBuilder().id(i).build();
            final boolean hasAssetWithId = updatedAssets.stream()
                    .map(Asset::getId)
                    .anyMatch(id -> id.equals(updatedAsset.getId()));

            if (hasAssetWithId) {
                throw new ValidationException("request.imp[%d].native.request.assets[%d].id is already being used by "
                        + "another asset. Each asset ID must be unique.", impIndex, i);
            }

            updatedAssets.add(updatedAsset);
        }
        return updatedAssets;
    }

    private void validateNativeAsset(Asset asset, int impIndex, int assetIndex) throws ValidationException {
        final TitleObject title = asset.getTitle();
        final ImageObject image = asset.getImg();
        final VideoObject video = asset.getVideo();
        final DataObject data = asset.getData();

        final long assetsCount = Stream.of(title, image, video, data)
                .filter(Objects::nonNull)
                .count();

        if (assetsCount > 1) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d] must define at most one of {title, img, video, data}",
                    impIndex, assetIndex);
        }

        validateNativeAssetTitle(title, impIndex, assetIndex);
        validateNativeAssetVideo(video, impIndex, assetIndex);
        validateNativeAssetData(data, impIndex, assetIndex);
    }

    private void validateNativeAssetTitle(TitleObject title, int impIndex, int assetIndex) throws ValidationException {
        if (title != null && (title.getLen() == null || title.getLen() < 1)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].title.len must be a positive integer",
                    impIndex, assetIndex);
        }
    }

    private void validateNativeAssetData(DataObject data, int impIndex, int assetIndex) throws ValidationException {
        if (data == null || data.getType() == null) {
            return;
        }

        final Integer type = data.getType();
        if (type < DataAssetType.SPONSORED.getValue()
                || (type > DataAssetType.CTA_TEXT.getValue() && type < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].data.type is invalid. See section 7.4: "
                            + documentationOnPage(40), impIndex, assetIndex);
        }
    }

    private void validateNativeAssetVideo(VideoObject video, int impIndex, int assetIndex) throws ValidationException {
        if (video == null) {
            return;
        }

        if (CollectionUtils.isEmpty(video.getMimes())) {
            throw new ValidationException("request.imp[%d].native.request.assets[%d].video.mimes must be an "
                    + "array with at least one MIME type", impIndex, assetIndex);
        }

        if (video.getMinduration() == null || video.getMinduration() < 1) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].video.minduration must be a positive integer",
                    impIndex, assetIndex);
        }

        if (video.getMaxduration() == null || video.getMaxduration() < 1) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].video.maxduration must be a positive integer",
                    impIndex, assetIndex);
        }

        validateNativeVideoProtocols(video.getProtocols(), impIndex, assetIndex);
    }

    private void validateNativeVideoProtocols(List<Integer> protocols, int impIndex, int assetIndex)
            throws ValidationException {
        if (CollectionUtils.isEmpty(protocols)) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].video.protocols must be an array with at least"
                            + " one element", impIndex, assetIndex);
        }

        for (int i = 0; i < protocols.size(); i++) {
            validateNativeVideoProtocol(protocols.get(i), impIndex, assetIndex, i);
        }
    }

    private void validateNativeVideoProtocol(Integer protocol, int impIndex, int assetIndex, int protocolIndex)
            throws ValidationException {
        if (protocol < Protocol.VAST10.getValue() || protocol > Protocol.DAAST10_WRAPPER.getValue()) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].video.protocols[%d] must be in the range [1, 10]."
                            + " Got %d", impIndex, assetIndex, protocolIndex, protocol);
        }
    }

    private void validateNativeEventTrackers(List<EventTracker> eventTrackers, int impIndex)
            throws ValidationException {

        if (CollectionUtils.isNotEmpty(eventTrackers)) {
            for (int eventTrackerIndex = 0; eventTrackerIndex < eventTrackers.size(); eventTrackerIndex++) {
                validateNativeEventTracker(eventTrackers.get(eventTrackerIndex), impIndex, eventTrackerIndex);
            }
        }
    }

    private void validateNativeEventTracker(EventTracker eventTracker, int impIndex, int eventIndex)
            throws ValidationException {
        if (eventTracker != null) {
            final int event = eventTracker.getEvent() != null ? eventTracker.getEvent() : 0;

            if (event != 0 && (event < EventType.IMPRESSION.getValue() || (event > EventType.VIEWABLE_VIDEO50.getValue()
                    && event < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND))) {
                throw new ValidationException(
                        "request.imp[%d].native.request.eventtrackers[%d].event is invalid. See section 7.6: "
                                + documentationOnPage(43), impIndex, eventIndex
                );
            }

            final List<Integer> methods = eventTracker.getMethods();

            if (CollectionUtils.isEmpty(methods)) {
                throw new ValidationException(
                        "request.imp[%d].native.request.eventtrackers[%d].method is required. See section 7.7: "
                                + documentationOnPage(43), impIndex, eventIndex
                );
            }

            for (int methodIndex = 0; methodIndex < methods.size(); methodIndex++) {
                final int method = methods.get(methodIndex) != null ? methods.get(methodIndex) : 0;
                if (method < EventTrackingMethod.IMAGE.getValue() || (method > EventTrackingMethod.JS.getValue()
                        && event < NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND)) {
                    throw new ValidationException(
                            "request.imp[%d].native.request.eventtrackers[%d].methods[%d] is invalid. See section 7.7: "
                                    + documentationOnPage(43), impIndex, eventIndex, methodIndex
                    );
                }
            }
        }
    }

    private static String documentationOnPage(int page) {
        return "%s#page=%d".formatted(DOCUMENTATION, page);
    }

    private String toEncodedRequest(Request nativeRequest, List<Asset> updatedAssets) {
        try {
            return mapper.mapper().writeValueAsString(nativeRequest.toBuilder().assets(updatedAssets).build());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error while marshaling native request to the string", e);
        }
    }

    private void validateImpExt(ObjectNode ext, Map<String, String> aliases, int impIndex,
                                List<String> warnings) throws ValidationException {
        validateImpExtPrebid(ext != null ? ext.get(PREBID_EXT) : null, aliases, impIndex, warnings);
    }

    private void validateImpExtPrebid(JsonNode extPrebidNode, Map<String, String> aliases, int impIndex,
                                      List<String> warnings)
            throws ValidationException {

        if (extPrebidNode == null) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid must be defined", impIndex);
        }

        if (!extPrebidNode.isObject()) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid must an object type", impIndex);
        }

        final JsonNode extPrebidBidderNode = extPrebidNode.get(BIDDER_EXT);

        if (extPrebidBidderNode != null && !extPrebidBidderNode.isObject()) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid.bidder must be an object type", impIndex);
        }
        final ExtImpPrebid extPrebid = parseExtImpPrebid((ObjectNode) extPrebidNode, impIndex);

        validateImpExtPrebidBidder(extPrebidBidderNode, extPrebid.getStoredAuctionResponse(),
                aliases, impIndex, warnings);
        validateImpExtPrebidStoredResponses(extPrebid, aliases, impIndex);
    }

    private void validateImpExtPrebidBidder(JsonNode extPrebidBidder,
                                            ExtStoredAuctionResponse storedAuctionResponse,
                                            Map<String, String> aliases,
                                            int impIndex,
                                            List<String> warnings) throws ValidationException {
        if (extPrebidBidder == null) {
            if (storedAuctionResponse != null) {
                return;
            } else {
                throw new ValidationException("request.imp[%d].ext.prebid.bidder must be defined", impIndex);
            }
        }

        final Iterator<Map.Entry<String, JsonNode>> bidderExtensions = extPrebidBidder.fields();
        while (bidderExtensions.hasNext()) {
            final Map.Entry<String, JsonNode> bidderExtension = bidderExtensions.next();
            final String bidder = bidderExtension.getKey();
            try {
                validateImpBidderExtName(impIndex, bidderExtension, aliases.getOrDefault(bidder, bidder));
            } catch (ValidationException ex) {
                bidderExtensions.remove();
                warnings.add("WARNING: request.imp[%d].ext.prebid.bidder.%s was dropped with a reason: %s"
                        .formatted(impIndex, bidder, ex.getMessage()));
            }
        }

        if (extPrebidBidder.size() == 0) {
            warnings.add("WARNING: request.imp[%d].ext must contain at least one valid bidder".formatted(impIndex));
        }
    }

    private void validateImpExtPrebidStoredResponses(ExtImpPrebid extPrebid,
                                                     Map<String, String> aliases,
                                                     int impIndex) throws ValidationException {
        final ExtStoredAuctionResponse extStoredAuctionResponse = extPrebid.getStoredAuctionResponse();
        if (extStoredAuctionResponse != null && extStoredAuctionResponse.getId() == null) {
            throw new ValidationException("request.imp[%d].ext.prebid.storedauctionresponse.id should be defined",
                    impIndex);
        }

        final List<ExtStoredBidResponse> storedBidResponses = extPrebid.getStoredBidResponse();
        if (CollectionUtils.isNotEmpty(storedBidResponses)) {
            final ObjectNode bidderNode = extPrebid.getBidder();
            if (bidderNode == null || bidderNode.isEmpty()) {
                throw new ValidationException(
                        "request.imp[%d].ext.prebid.bidder should be defined for storedbidresponse"
                                .formatted(impIndex));
            }

            for (ExtStoredBidResponse storedBidResponse : storedBidResponses) {
                validateStoredBidResponse(storedBidResponse, bidderNode, aliases, impIndex);
            }
        }
    }

    private void validateStoredBidResponse(ExtStoredBidResponse extStoredBidResponse, ObjectNode bidderNode,
                                           Map<String, String> aliases, int impIndex) throws ValidationException {
        final String bidder = extStoredBidResponse.getBidder();
        final String id = extStoredBidResponse.getId();
        if (StringUtils.isEmpty(bidder)) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid.storedbidresponse.bidder was not defined".formatted(impIndex));
        }

        if (StringUtils.isEmpty(id)) {
            throw new ValidationException(
                    "Id was not defined for request.imp[%d].ext.prebid.storedbidresponse.id".formatted(impIndex));
        }

        final String resolvedBidder = aliases.getOrDefault(bidder, bidder);

        if (!bidderCatalog.isValidName(resolvedBidder)) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid.storedbidresponse.bidder is not valid bidder".formatted(impIndex));
        }

        final boolean noCorrespondentBidderParameters = StreamUtil.asStream(bidderNode.fieldNames())
                .noneMatch(impBidder -> impBidder.equals(resolvedBidder) || impBidder.equals(bidder));
        if (noCorrespondentBidderParameters) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid.storedbidresponse.bidder does not have correspondent bidder parameters"
                            .formatted(impIndex));
        }
    }

    private ExtImpPrebid parseExtImpPrebid(ObjectNode extImpPrebid, int impIndex) throws ValidationException {
        try {
            return mapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException(" bidRequest.imp[%d].ext.prebid: %s has invalid format"
                    .formatted(impIndex, e.getMessage()));
        }
    }

    private void validateImpBidderExtName(int impIndex, Map.Entry<String, JsonNode> bidderExtension, String bidderName)
            throws ValidationException {
        if (bidderCatalog.isValidName(bidderName)) {
            final Set<String> messages = bidderParamValidator.validate(bidderName, bidderExtension.getValue());
            if (!messages.isEmpty()) {
                throw new ValidationException("request.imp[%d].ext.prebid.bidder.%s failed validation.\n%s", impIndex,
                        bidderName, String.join("\n", messages));
            }
        } else if (!bidderCatalog.isDeprecatedName(bidderName)) {
            throw new ValidationException(
                    "request.imp[%d].ext.prebid.bidder contains unknown bidder: %s", impIndex, bidderName);
        }
    }

    private void validatePmp(Pmp pmp, int impIndex) throws ValidationException {
        if (pmp != null && pmp.getDeals() != null) {
            for (int dealIndex = 0; dealIndex < pmp.getDeals().size(); dealIndex++) {
                if (StringUtils.isBlank(pmp.getDeals().get(dealIndex).getId())) {
                    throw new ValidationException("request.imp[%d].pmp.deals[%d] missing required field: \"id\"",
                            impIndex, dealIndex);
                }
            }
        }
    }

    private void validateBanner(Banner banner, boolean isInterstitial, int impIndex) throws ValidationException {
        if (banner != null) {
            final Integer width = banner.getW();
            final Integer height = banner.getH();
            final boolean hasWidth = hasPositiveValue(width);
            final boolean hasHeight = hasPositiveValue(height);
            final boolean hasSize = hasWidth && hasHeight;

            final List<Format> format = banner.getFormat();
            if (CollectionUtils.isEmpty(format) && !hasSize && !isInterstitial) {
                throw new ValidationException("request.imp[%d].banner has no sizes. Define \"w\" and \"h\", "
                        + "or include \"format\" elements", impIndex);
            }

            if (width != null && height != null && !hasSize && !isInterstitial) {
                throw new ValidationException("Request imp[%d].banner must define a valid"
                        + " \"h\" and \"w\" properties", impIndex);
            }

            if (format != null) {
                for (int formatIndex = 0; formatIndex < format.size(); formatIndex++) {
                    validateFormat(format.get(formatIndex), impIndex, formatIndex);
                }
            }
        }
    }

    private void validateFormat(Format format, int impIndex, int formatIndex) throws ValidationException {
        final boolean usesH = hasPositiveValue(format.getH());
        final boolean usesW = hasPositiveValue(format.getW());
        final boolean usesWmin = hasPositiveValue(format.getWmin());
        final boolean usesWratio = hasPositiveValue(format.getWratio());
        final boolean usesHratio = hasPositiveValue(format.getHratio());
        final boolean usesHW = usesH || usesW;
        final boolean usesRatios = usesWmin || usesWratio || usesHratio;

        if (usesHW && usesRatios) {
            throw new ValidationException("Request imp[%d].banner.format[%d] should define *either*"
                    + " {w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" "
                    + "objects in the request", impIndex, formatIndex);
        }

        if (!usesHW && !usesRatios) {
            throw new ValidationException("Request imp[%d].banner.format[%d] should define *either*"
                    + " {w, h} (for static size requirements) *or* {wmin, wratio, hratio} (for flexible sizes) "
                    + "to be non-zero positive", impIndex, formatIndex);
        }

        if (usesHW && (!usesH || !usesW)) {
            throw new ValidationException("Request imp[%d].banner.format[%d] must define a valid"
                    + " \"h\" and \"w\" properties", impIndex, formatIndex);
        }

        if (usesRatios && (!usesWmin || !usesWratio || !usesHratio)) {
            throw new ValidationException("Request imp[%d].banner.format[%d] must define a valid"
                    + " \"wmin\", \"wratio\", and \"hratio\" properties", impIndex, formatIndex);
        }
    }

    private void validateVideoMimes(Video video, int impIndex) throws ValidationException {
        if (video != null) {
            validateMimes(video.getMimes(),
                    "request.imp[%d].video.mimes must contain at least one supported MIME type", impIndex);
        }
    }

    private void validateAudioMimes(Audio audio, int impIndex) throws ValidationException {
        if (audio != null) {
            validateMimes(audio.getMimes(),
                    "request.imp[%d].audio.mimes must contain at least one supported MIME type", impIndex);
        }
    }

    private void validateMimes(List<String> mimes, String msg, int index) throws ValidationException {
        if (CollectionUtils.isEmpty(mimes)) {
            throw new ValidationException(msg, index);
        }
    }

    private void validateMetrics(List<Metric> metrics, int impIndex) throws ValidationException {
        for (int i = 0; i < metrics.size(); i++) {
            final Metric metric = metrics.get(i);

            if (StringUtils.isEmpty(metric.getType())) {
                throw new ValidationException("Missing request.imp[%d].metric[%d].type", impIndex, i);
            }

            final Float value = metric.getValue();
            if (value == null || value < 0.0 || value > 1.0) {
                throw new ValidationException("request.imp[%d].metric[%d].value must be in the range [0.0, 1.0]",
                        impIndex, i);
            }
        }
    }

    private static boolean hasPositiveValue(Integer value) {
        return value != null && value > 0;
    }
}
