package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.TitleObject;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredBidResponse;
import org.prebid.server.util.StreamUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class ImpValidator {

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final Integer NATIVE_EXCHANGE_SPECIFIC_LOWER_BOUND = 500;

    private static final String DOCUMENTATION = "https://iabtechlab.com/wp-content/uploads/2016/07/"
            + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf";
    private static final String IMP_EXT = "imp";

    private final BidderParamValidator bidderParamValidator;
    private final BidderCatalog bidderCatalog;
    private final JacksonMapper mapper;

    public ImpValidator(BidderParamValidator bidderParamValidator, BidderCatalog bidderCatalog, JacksonMapper mapper) {
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public void validateImps(List<Imp> imps,
                             Map<String, String> aliases,
                             List<String> warnings) throws ValidationException {

        for (int i = 0; i < imps.size(); i++) {
            final Imp imp = imps.get(i);
            validateImp(imp, "request.imp[%d]".formatted(i));
            fillAndValidateNative(imp.getXNative(), i);
            validateImpExt(imp.getExt(), aliases, i, warnings);
        }
    }

    public void validateImp(Imp imp) throws ValidationException {
        validateImp(imp, "imp[id=%s]".formatted(imp.getId()));
    }

    private void validateImp(Imp imp, String msgPrefix) throws ValidationException {
        if (StringUtils.isBlank(imp.getId())) {
            throw new ValidationException("%s missing required field: \"id\"", msgPrefix);
        }
        if (imp.getMetric() != null && !imp.getMetric().isEmpty()) {
            validateMetrics(imp.getMetric(), msgPrefix);
        }
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getAudio() == null && imp.getXNative() == null) {
            throw new ValidationException(
                    "%s must contain at least one of \"banner\", \"video\", \"audio\", or \"native\"",
                    msgPrefix);
        }

        final boolean isInterstitialImp = Objects.equals(imp.getInstl(), 1);
        validateBanner(imp.getBanner(), isInterstitialImp, msgPrefix);
        validateVideoMimes(imp.getVideo(), msgPrefix);
        validateAudioMimes(imp.getAudio(), msgPrefix);
        validatePmp(imp.getPmp(), msgPrefix);
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
        validateImpExtPrebidStoredResponses(extPrebid, aliases, impIndex, warnings);

        validateImpExtPrebidImp(extPrebidNode.get(IMP_EXT), aliases, impIndex, warnings);
    }

    private void validateImpExtPrebidImp(JsonNode imp,
                                         Map<String, String> aliases,
                                         int impIndex,
                                         List<String> warnings) {
        if (imp == null) {
            return;
        }

        final Iterator<Map.Entry<String, JsonNode>> bidders = imp.fields();
        while (bidders.hasNext()) {
            final Map.Entry<String, JsonNode> bidder = bidders.next();
            final String bidderName = bidder.getKey();
            final String resolvedBidderName = aliases.getOrDefault(bidderName, bidderName);
            if (!bidderCatalog.isValidName(resolvedBidderName) && !bidderCatalog.isDeprecatedName(resolvedBidderName)) {
                bidders.remove();
                warnings.add("WARNING: request.imp[%d].ext.prebid.imp.%s was dropped with the reason: invalid bidder"
                        .formatted(impIndex, bidderName));
            }
        }
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

        if (extPrebidBidder.isEmpty()) {
            warnings.add("WARNING: request.imp[%d].ext must contain at least one valid bidder".formatted(impIndex));
        }
    }

    private void validateImpExtPrebidStoredResponses(ExtImpPrebid extPrebid,
                                                     Map<String, String> aliases,
                                                     int impIndex,
                                                     List<String> warnings) throws ValidationException {
        final ExtStoredAuctionResponse extStoredAuctionResponse = extPrebid.getStoredAuctionResponse();
        if (extStoredAuctionResponse != null) {
            if (extStoredAuctionResponse.getSeatBids() != null) {
                warnings.add("WARNING: request.imp[%d].ext.prebid.storedauctionresponse.seatbidarr".formatted(impIndex)
                        + " is not supported at the imp level");
            }

            if (extStoredAuctionResponse.getId() == null && extStoredAuctionResponse.getSeatBid() == null) {
                throw new ValidationException(
                        "request.imp[%d].ext.prebid.storedauctionresponse.{id or seatbidobj} should be defined",
                        impIndex);
            }
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

    private void validatePmp(Pmp pmp, String msgPrefix) throws ValidationException {
        if (pmp != null && pmp.getDeals() != null) {
            for (int dealIndex = 0; dealIndex < pmp.getDeals().size(); dealIndex++) {
                if (StringUtils.isBlank(pmp.getDeals().get(dealIndex).getId())) {
                    throw new ValidationException("%s.pmp.deals[%d] missing required field: \"id\"",
                            msgPrefix, dealIndex);
                }
            }
        }
    }

    private void validateBanner(Banner banner, boolean isInterstitial, String msgPrefix) throws ValidationException {
        if (banner != null) {
            final Integer width = banner.getW();
            final Integer height = banner.getH();
            final boolean hasWidth = hasPositiveValue(width);
            final boolean hasHeight = hasPositiveValue(height);
            final boolean hasSize = hasWidth && hasHeight;

            final List<Format> format = banner.getFormat();
            if (CollectionUtils.isEmpty(format) && !hasSize && !isInterstitial) {
                throw new ValidationException("%s.banner has no sizes. Define \"w\" and \"h\", "
                        + "or include \"format\" elements", msgPrefix);
            }

            if (width != null && height != null && !hasSize && !isInterstitial) {
                throw new ValidationException("%s.banner must define a valid"
                        + " \"h\" and \"w\" properties", msgPrefix);
            }

            if (format != null) {
                for (int formatIndex = 0; formatIndex < format.size(); formatIndex++) {
                    validateFormat(format.get(formatIndex), msgPrefix, formatIndex);
                }
            }
        }
    }

    private void validateFormat(Format format, String msgPrefix, int formatIndex) throws ValidationException {
        final boolean usesH = hasPositiveValue(format.getH());
        final boolean usesW = hasPositiveValue(format.getW());
        final boolean usesWmin = hasPositiveValue(format.getWmin());
        final boolean usesWratio = hasPositiveValue(format.getWratio());
        final boolean usesHratio = hasPositiveValue(format.getHratio());
        final boolean usesHW = usesH || usesW;
        final boolean usesRatios = usesWmin || usesWratio || usesHratio;

        if (usesHW && usesRatios) {
            throw new ValidationException("%s.banner.format[%d] should define *either*"
                    + " {w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" "
                    + "objects in the request", msgPrefix, formatIndex);
        }

        if (!usesHW && !usesRatios) {
            throw new ValidationException("%s.banner.format[%d] should define *either*"
                    + " {w, h} (for static size requirements) *or* {wmin, wratio, hratio} (for flexible sizes) "
                    + "to be non-zero positive", msgPrefix, formatIndex);
        }

        if (usesHW && (!usesH || !usesW)) {
            throw new ValidationException("%s.banner.format[%d] must define a valid"
                    + " \"h\" and \"w\" properties", msgPrefix, formatIndex);
        }

        if (usesRatios && (!usesWmin || !usesWratio || !usesHratio)) {
            throw new ValidationException("%s.banner.format[%d] must define a valid"
                    + " \"wmin\", \"wratio\", and \"hratio\" properties", msgPrefix, formatIndex);
        }
    }

    private void validateVideoMimes(Video video, String msgPrefix) throws ValidationException {
        if (video != null) {
            validateMimes(video.getMimes(),
                    "%s.video.mimes must contain at least one supported MIME type", msgPrefix);
        }
    }

    private void validateAudioMimes(Audio audio, String msgPrefix) throws ValidationException {
        if (audio != null) {
            validateMimes(audio.getMimes(),
                    "%s.audio.mimes must contain at least one supported MIME type", msgPrefix);
        }
    }

    private void validateMimes(List<String> mimes, String msg, String msgPrefix) throws ValidationException {
        if (CollectionUtils.isEmpty(mimes)) {
            throw new ValidationException(msg, msgPrefix);
        }
    }

    private void validateMetrics(List<Metric> metrics, String msgPrefix) throws ValidationException {
        for (int i = 0; i < metrics.size(); i++) {
            final Metric metric = metrics.get(i);

            if (StringUtils.isEmpty(metric.getType())) {
                throw new ValidationException("Missing %s.metric[%d].type", msgPrefix, i);
            }

            final Float value = metric.getValue();
            if (value == null || value < 0.0 || value > 1.0) {
                throw new ValidationException("%s.metric[%d].value must be in the range [0.0, 1.0]", msgPrefix, i);
            }
        }
    }

    private String toEncodedRequest(Request nativeRequest, List<Asset> updatedAssets) {
        try {
            return mapper.mapper().writeValueAsString(nativeRequest.toBuilder().assets(updatedAssets).build());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error while marshaling native request to the string", e);
        }
    }

    private static String documentationOnPage(int page) {
        return "%s#page=%d".formatted(DOCUMENTATION, page);
    }

    private static boolean hasPositiveValue(Integer value) {
        return value != null && value > 0;
    }

}
