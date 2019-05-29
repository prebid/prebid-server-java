package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Device;
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
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTpId;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A component that validates {@link BidRequest} objects for openrtb2 auction endpoint.
 * Validations are processed by the validate method and returns {@link ValidationResult}.
 */
public class RequestValidator {

    private static final String PREBID_EXT = "prebid";
    private static final String CONTEXT_EXT = "context";
    private static final Locale LOCALE = Locale.US;

    private final BidderCatalog bidderCatalog;
    private final BidderParamValidator bidderParamValidator;

    /**
     * Constructs a RequestValidator that will use the BidderParamValidator passed in order to validate all critical
     * properties of bidRequest.
     */
    public RequestValidator(BidderCatalog bidderCatalog, BidderParamValidator bidderParamValidator) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    /**
     * Validates the {@link BidRequest} against a list of validation checks, however, reports only one problem
     * at a time.
     */
    public ValidationResult validate(BidRequest bidRequest) {
        try {
            if (StringUtils.isBlank(bidRequest.getId())) {
                throw new ValidationException("request missing required field: \"id\"");
            }

            if (bidRequest.getTmax() != null && bidRequest.getTmax() < 0L) {
                throw new ValidationException("request.tmax must be nonnegative. Got %s", bidRequest.getTmax());
            }

            validateCur(bidRequest.getCur());

            final ExtBidRequest extBidRequest = parseAndValidateExtBidRequest(bidRequest);

            final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;

            Map<String, String> aliases = Collections.emptyMap();

            if (extRequestPrebid != null) {
                final ExtRequestTargeting targeting = extRequestPrebid.getTargeting();
                if (targeting != null) {
                    validateTargeting(targeting);
                }
                aliases = ObjectUtils.defaultIfNull(extRequestPrebid.getAliases(), Collections.emptyMap());
                validateAliases(aliases);
                validateBidAdjustmentFactors(
                        ObjectUtils.defaultIfNull(extRequestPrebid.getBidadjustmentfactors(), Collections.emptyMap()),
                        aliases);
            }

            if (CollectionUtils.isEmpty(bidRequest.getImp())) {
                throw new ValidationException("request.imp must contain at least one element");
            }

            final List<Imp> imps = bidRequest.getImp();
            final List<String> errors = new ArrayList<>();
            final Map<String, Integer> uniqueImps = new HashMap<>();
            for (int i = 0; i < imps.size(); i++) {
                String impId = imps.get(i).getId();
                if (uniqueImps.get(impId) != null) {
                    errors.add(String.format(
                            "request.imp[%d].id and request.imp[%d].id are both \"%s\". Imp IDs must be unique.",
                            uniqueImps.get(impId), i, impId));
                }

                uniqueImps.put(impId, i);
            }

            if (CollectionUtils.isNotEmpty(errors)) {
                throw new ValidationException(String.join(System.lineSeparator(), errors));
            }

            for (int index = 0; index < bidRequest.getImp().size(); index++) {
                validateImp(bidRequest.getImp().get(index), aliases, index);
            }

            if ((bidRequest.getSite() == null && bidRequest.getApp() == null)
                    || (bidRequest.getSite() != null && bidRequest.getApp() != null)) {

                throw new ValidationException("request.site or request.app must be defined, but not both");
            }
            validateSite(bidRequest.getSite());
            validateApp(bidRequest.getApp());
            validateDevice(bidRequest.getDevice());
            validateUser(bidRequest.getUser(), aliases);
            validateRegs(bidRequest.getRegs());
        } catch (ValidationException ex) {
            return ValidationResult.error(ex.getMessage());
        }
        return ValidationResult.success();
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

    private void validateBidAdjustmentFactors(Map<String, BigDecimal> adjustmentFactors, Map<String, String> aliases)
            throws ValidationException {

        for (Map.Entry<String, BigDecimal> bidderAdjustment : adjustmentFactors.entrySet()) {
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
    private static void validateTargeting(ExtRequestTargeting extRequestTargeting) throws ValidationException {
        validateExtPriceGranularity(extRequestTargeting.getPricegranularity(), null);
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
    private static void validateExtPriceGranularity(JsonNode priceGranularity, BidType type)
            throws ValidationException {
        final ExtPriceGranularity extPriceGranularity;
        try {
            extPriceGranularity = Json.mapper.treeToValue(priceGranularity, ExtPriceGranularity.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException(String.format("Error while parsing request.ext.prebid.targeting.%s",
                    type == null ? "pricegranularity" : "mediatypepricegranularity." + type));
        }

        final Integer precision = extPriceGranularity.getPrecision();
        if (precision != null && precision < 0) {
            throw new ValidationException(String.format("%srice granularity error: precision must be non-negative",
                    type == null ? "P" : StringUtils.capitalize(type.name()) + " p"));
        }
        validateGranularityRanges(extPriceGranularity.getRanges());
    }

    /**
     * Validates {@link ExtMediaTypePriceGranularity} if it's present.
     */
    private static void validateMediaTypePriceGranularity(ExtMediaTypePriceGranularity mediaTypePriceGranularity)
            throws ValidationException {
        if (mediaTypePriceGranularity != null) {
            final JsonNode banner = mediaTypePriceGranularity.getBanner();
            final JsonNode video = mediaTypePriceGranularity.getVideo();
            final JsonNode xNative = mediaTypePriceGranularity.getXNative();
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

        final Iterator<ExtGranularityRange> rangeIterator = ranges.iterator();
        ExtGranularityRange range = rangeIterator.next();
        validateGranularityRangeIncrement(range);

        while (rangeIterator.hasNext()) {
            final ExtGranularityRange nextGranularityRange = rangeIterator.next();
            if (range.getMax().compareTo(nextGranularityRange.getMax()) > 0) {
                throw new ValidationException(
                        "Price granularity error: range list must be ordered with increasing \"max\"");
            }
            validateGranularityRangeIncrement(nextGranularityRange);
            range = nextGranularityRange;
        }
    }

    /**
     * Validates {@link ExtGranularityRange}s increment.
     */
    private static void validateGranularityRangeIncrement(ExtGranularityRange range)
            throws ValidationException {
        if (range.getIncrement().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Price granularity error: increment must be a nonzero positive number");
        }
    }

    private ExtBidRequest parseAndValidateExtBidRequest(BidRequest bidRequest) throws ValidationException {
        ExtBidRequest extBidRequest = null;
        if (bidRequest.getExt() != null) {
            try {
                extBidRequest = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.ext is invalid: %s", e.getMessage());
            }
        }
        return extBidRequest;
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
                throw new ValidationException(String.format(
                        "request.ext.prebid.aliases.%s refers to unknown bidder: %s", alias, coreBidder));
            }
            if (alias.equals(coreBidder)) {
                throw new ValidationException(String.format("request.ext.prebid.aliases.%s defines a no-op alias. "
                        + "Choose a different alias, or remove this entry", alias));
            }
        }
    }

    private void validateSite(Site site) throws ValidationException {
        if (site != null) {
            if (StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
                throw new ValidationException(
                        "request.site should include at least one of request.site.id or request.site.page");
            }

            final ObjectNode siteExt = site.getExt();
            if (siteExt != null && siteExt.size() > 0) {
                try {
                    final ExtSite extSite = Json.mapper.treeToValue(siteExt, ExtSite.class);
                    final Integer amp = extSite.getAmp();
                    if (amp != null && (amp < 0 || amp > 1)) {
                        throw new ValidationException("request.site.ext.amp must be either 1, 0, or undefined");
                    }
                } catch (JsonProcessingException e) {
                    throw new ValidationException("request.site.ext object is not valid: %s", e.getMessage());
                }
            }
        }
    }

    private void validateApp(App app) throws ValidationException {
        if (app != null && app.getExt() != null) {
            try {
                Json.mapper.treeToValue(app.getExt(), ExtApp.class);
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.app.ext object is not valid: %s", e.getMessage());
            }
        }
    }

    private void validateDevice(Device device) throws ValidationException {
        final ObjectNode extDeviceNode = device != null ? device.getExt() : null;
        if (extDeviceNode != null && extDeviceNode.size() > 0) {
            final ExtDevice extDevice = parseExtDevice(extDeviceNode);
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

    private ExtDevice parseExtDevice(ObjectNode extDevice) throws ValidationException {
        try {
            return Json.mapper.treeToValue(extDevice, ExtDevice.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("request.device.ext is not valid", e.getMessage());
        }
    }

    private void validateUser(User user, Map<String, String> aliases) throws ValidationException {
        if (user != null && user.getExt() != null) {
            try {
                final ExtUser extUser = Json.mapper.treeToValue(user.getExt(), ExtUser.class);
                final ExtUserDigiTrust digitrust = extUser.getDigitrust();
                final ExtUserPrebid prebid = extUser.getPrebid();
                final List<ExtUserTpId> tpid = extUser.getTpid();

                if (digitrust != null && digitrust.getPref() != 0) {
                    throw new ValidationException("request.user contains a digitrust object that is not valid");
                }

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

                if (tpid != null) {
                    if (tpid.isEmpty()) {
                        throw new ValidationException(
                                "request.user.ext.tpid must contain at least one element or be undefined");
                    }
                    for (int index = 0; index < tpid.size(); index++) {
                        final ExtUserTpId extUserTpId = tpid.get(index);
                        if (StringUtils.isBlank(extUserTpId.getSource())) {
                            throw new ValidationException(
                                    "request.user.ext.tpid[%s].source missing required field: \"source\"", index);
                        }
                        if (StringUtils.isBlank(extUserTpId.getUid())) {
                            throw new ValidationException(
                                    "request.user.ext.tpid[%s].uid missing required field: \"uid\"", index);
                        }
                    }
                }
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.user.ext object is not valid: %s", e.getMessage());
            }
        }
    }

    /**
     * Validates {@link Regs}. Throws {@link ValidationException} in case if {@link ExtRegs} is present in
     * bidrequest.regs.ext and its gdpr value has different value to 0 or 1.
     */
    private void validateRegs(Regs regs) throws ValidationException {
        if (regs != null) {
            try {
                final ExtRegs extRegs = Json.mapper.treeToValue(regs.getExt(), ExtRegs.class);
                final Integer gdpr = extRegs == null ? null : extRegs.getGdpr();
                if (gdpr != null && (gdpr < 0 || gdpr > 1)) {
                    throw new ValidationException("request.regs.ext.gdpr must be either 0 or 1");
                }
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.regs.ext is invalid: %s", e.getMessage());
            }
        }
    }

    private void validateImp(Imp imp, Map<String, String> aliases, int index) throws ValidationException {
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

        validateBanner(imp.getBanner(), index);
        validateVideoMimes(imp.getVideo(), index);
        validateAudioMimes(imp.getAudio(), index);
        fillAndValidateNative(imp.getXNative(), index);
        validatePmp(imp.getPmp(), index);
        validateImpExt(imp.getExt(), aliases, index);
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

    private static Request parseNativeRequest(String rawStringNativeRequest, int impIndex) throws ValidationException {
        if (StringUtils.isBlank(rawStringNativeRequest)) {
            throw new ValidationException("request.imp.[%s].ext.native contains empty request value", impIndex);
        }
        try {
            return Json.mapper.readValue(rawStringNativeRequest, Request.class);
        } catch (IOException e) {
            throw new ValidationException(
                    "Error while parsing request.imp.[%s].ext.native.request", impIndex);
        }
    }

    private void validateNativeContextTypes(Integer context, Integer contextSubType, int index)
            throws ValidationException {

        final int type = context != null ? context : 0;
        final int subType = contextSubType != null ? contextSubType : 0;

        if (type != 0 && (type < ContextType.CONTENT.getValue() || type > ContextType.PRODUCT.getValue())) {
            throw new ValidationException(
                    "request.imp[%d].native.request.context is invalid. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39",
                    index);
        }

        if (subType < 0) {
            throw new ValidationException(
                    "request.imp[%d].native.request.contextsubtype is invalid. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39",
                    index);
        }

        if (subType == 0) {
            return;
        }

        if (subType >= 100) {
            throw new ValidationException(
                    "request.imp[%d].native.request.contextsubtype is invalid. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39",
                    index);
        }

        if (subType >= ContextSubType.GENERAL.getValue() && subType <= ContextSubType.USER_GENERATED.getValue()
                && type != ContextType.CONTENT.getValue()) {
            throw new ValidationException(
                    "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                            + "combination. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39",
                    index, context, contextSubType);
        }

        if (subType >= ContextSubType.SOCIAL.getValue() && subType <= ContextSubType.CHAT.getValue()
                && type != ContextType.SOCIAL.getValue()) {
            throw new ValidationException(
                    "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                            + "combination. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2"
                            + ".pdf#page=39", index, context, contextSubType);
        }

        if (subType >= ContextSubType.SELLING.getValue() && subType <= ContextSubType.PRODUCT_REVIEW.getValue()
                && type != ContextType.PRODUCT.getValue()) {
            throw new ValidationException(
                    "request.imp[%d].native.request.context is %d, but contextsubtype is %d. This is an invalid "
                            + "combination. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2"
                            + ".pdf#page=39", index, context, contextSubType);
        }
    }

    private void validateNativePlacementType(Integer placementType, int index) throws ValidationException {
        if (placementType != null && (placementType < PlacementType.FEED.getValue()
                || placementType > PlacementType.RECOMMENDATION_WIDGET.getValue())) {
            throw new ValidationException(
                    "request.imp[%d].native.request.plcmttype is invalid. See https://iabtechlab"
                            + ".com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=40",
                    index, placementType);
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
            updatedAssets.add(asset.toBuilder().id(i).build());
        }
        return updatedAssets;
    }

    private void validateNativeAsset(Asset asset, int impIndex, int assetIndex) throws ValidationException {
        if (asset.getId() != null) {
            throw new ValidationException("request.imp[%d].native.request.assets[%d].id must not be defined. Prebid"
                    + " Server will set this automatically, using the index of the asset in the array as the ID",
                    impIndex, assetIndex);
        }

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
        validateNativeAssetImage(image, impIndex, assetIndex);
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

    private void validateNativeAssetImage(ImageObject image, int impIndex, int assetIndex) throws ValidationException {
        if (image == null) {
            return;
        }

        final boolean isNotPresentWidth = image.getW() == null || image.getW() == 0;
        final boolean isNotPresentWidthMin = image.getWmin() == null || image.getWmin() == 0;
        if (isNotPresentWidth && isNotPresentWidthMin) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].img must contain at least one of \"w\" or \"wmin\"",
                    impIndex, assetIndex);
        }

        final boolean isNotPresentHeight = image.getH() == null || image.getH() == 0;
        final boolean isNotPresentHeightMin = image.getHmin() == null || image.getHmin() == 0;
        if (isNotPresentHeight && isNotPresentHeightMin) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].img must contain at least one of \"h\" or \"hmin\"",
                    impIndex, assetIndex);
        }
    }

    private void validateNativeAssetData(DataObject data, int impIndex, int assetIndex) throws ValidationException {
        if (data == null || data.getType() == null) {
            return;
        }

        final Integer type = data.getType();
        if (type < DataAssetType.SPONSORED.getValue() || type > DataAssetType.CTA_TEXT.getValue()) {
            throw new ValidationException(
                    "request.imp[%d].native.request.assets[%d].data.type must in the range [1, 12]. Got %d",
                    impIndex, assetIndex, type);
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

            if (event != 0 && (event < EventType.IMPRESSION.getValue()
                    || event > EventType.VIEWABLE_VIDEO50.getValue())) {
                throw new ValidationException(
                        "request.imp[%d].native.request.eventtrackers[%d].event is invalid. See section 7.6: "
                                + "https://iabtechlab.com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification"
                                + "-Final-1.2.pdf#page=43", impIndex, eventIndex
                );
            }

            final List<Integer> methods = eventTracker.getMethods();

            if (CollectionUtils.isEmpty(methods)) {
                throw new ValidationException(
                        "request.imp[%d].native.request.eventtrackers[%d].method is required. See section 7.7: "
                                + "https://iabtechlab.com/wp-content/uploads/2016/07/OpenRTB-Native-Ads-Specification"
                                + "-Final-1.2.pdf#page=43", impIndex, eventIndex
                );
            }

            for (int methodIndex = 0; methodIndex < methods.size(); methodIndex++) {
                int method = methods.get(methodIndex) != null ? methods.get(methodIndex) : 0;
                if (method < EventTrackingMethod.IMAGE.getValue()
                        || method > EventTrackingMethod.JS.getValue()) {
                    throw new ValidationException(
                            "request.imp[%d].native.request.eventtrackers[%d].methods[%d] is invalid. See section 7"
                                    + ".7: https://iabtechlab.com/wp-content/uploads/2016/07/OpenRTB-Native-Ads"
                                    + "-Specification-Final-1.2.pdf#page=43", impIndex, eventIndex, methodIndex
                    );
                }
            }
        }
    }

    private static String toEncodedRequest(Request nativeRequest, List<Asset> updatedAssets) {
        try {
            return Json.mapper.writeValueAsString(nativeRequest.toBuilder().assets(updatedAssets).build());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error while marshaling native request to the string", e);
        }
    }

    private void validateImpExt(ObjectNode ext, Map<String, String> aliases, int impIndex) throws ValidationException {
        if (ext == null || ext.size() < 1) {
            throw new ValidationException("request.imp[%s].ext must contain at least one bidder", impIndex);
        }

        final Iterator<Map.Entry<String, JsonNode>> bidderExtensions = ext.fields();
        while (bidderExtensions.hasNext()) {
            final Map.Entry<String, JsonNode> bidderExtension = bidderExtensions.next();
            final String bidder = bidderExtension.getKey();
            if (!Objects.equals(bidder, PREBID_EXT) && !Objects.equals(bidder, CONTEXT_EXT)) {
                validateImpBidderExtName(impIndex, bidderExtension, aliases.getOrDefault(bidder, bidder));
            }
        }
    }

    private void validateImpBidderExtName(int impIndex, Map.Entry<String, JsonNode> bidderExtension, String bidderName)
            throws ValidationException {
        if (bidderCatalog.isValidName(bidderName)) {
            final Set<String> messages = bidderParamValidator.validate(bidderName, bidderExtension.getValue());
            if (!messages.isEmpty()) {
                throw new ValidationException("request.imp[%d].ext.%s failed validation.\n%s", impIndex,
                        bidderName, String.join("\n", messages));
            }
        } else if (!bidderCatalog.isDeprecatedName(bidderName) && !bidderCatalog.isAlias(bidderName)) {
            throw new ValidationException(
                    "request.imp[%d].ext contains unknown bidder: %s", impIndex, bidderName);
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

    private void validateBanner(Banner banner, int impIndex) throws ValidationException {
        if (banner != null) {
            final Integer width = banner.getW();
            final Integer height = banner.getH();
            final boolean hasWidth = hasPositiveValue(width);
            final boolean hasHeight = hasPositiveValue(height);
            final boolean hasSize = hasWidth && hasHeight;

            final List<Format> format = banner.getFormat();
            if (CollectionUtils.isEmpty(format) && !hasSize) {
                throw new ValidationException("request.imp[%d].banner has no sizes. Define \"w\" and \"h\", "
                        + "or include \"format\" elements", impIndex);
            }

            if (width != null && height != null && !hasSize) {
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
            if (metrics.get(i).getType() == null || metrics.get(i).getType().isEmpty()) {
                throw new ValidationException("Missing request.imp[%d].metric[%d].type", impIndex, i);
            }

            if (metrics.get(i).getValue() < 0.0 || metrics.get(i).getValue() > 1.0) {
                throw new ValidationException("request.imp[%d].metric[%d].value must be in the range [0.0, 1.0]",
                        impIndex, i);
            }
        }
    }

    private static boolean hasPositiveValue(Integer value) {
        return value != null && value > 0;
    }
}
