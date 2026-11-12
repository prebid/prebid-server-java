package org.prebid.server.bidder.ocm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ocm.ExtImpOcm;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Routes bid requests to the Orange Click Media exchange, which is itself a Prebid Server holding
 * stored impression configurations. The placementId param therefore names an impression-level
 * stored request rather than a plain slot, and publisherId selects the account that resolves it.
 */
public class OcmBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOcm>> OCM_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String BIDDER_FIELD = "bidder";
    private static final String PREBID_FIELD = "prebid";
    private static final String STORED_REQUEST_FIELD = "storedrequest";
    private static final String ID_FIELD = "id";
    private static final String DEFAULT_CURRENCY = "USD";

    /**
     * Headroom deducted from tmax when no impression sets tmaxBufferMs. It covers the host's own
     * processing, the round trip to the OCM endpoint and that endpoint's response preparation.
     */
    private static final long DEFAULT_TMAX_BUFFER_MS = 250L;

    private static final Set<BidType> SUPPORTED_BID_TYPES = Set.of(BidType.banner, BidType.video, BidType.xNative);

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OcmBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Packs every impression into a single request. The publisherId param becomes the request-level
     * publisher id, so all impressions must agree on it; a divergent value is a misconfiguration
     * that rejects the whole request rather than a single impression.
     * <p>
     * The OCM endpoint is another Prebid Server, so tmax is reduced by a buffer before forwarding;
     * see {@link #reduceTmax(Long, long)}.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        String publisherId = null;
        Integer tmaxBufferMs = null;

        for (Imp imp : request.getImp()) {
            final ExtImpOcm extImpOcm;
            try {
                extImpOcm = parseImpExt(imp);
                validateImpExt(extImpOcm, imp.getId());
                publisherId = resolvePublisherId(publisherId, extImpOcm.getPublisherId(), imp.getId());
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            tmaxBufferMs = resolveTmaxBufferMs(tmaxBufferMs, extImpOcm.getTmaxBufferMs());
            modifiedImps.add(modifyImp(imp, extImpOcm.getPlacementId()));
        }

        final long bufferMs = tmaxBufferMs != null ? tmaxBufferMs : DEFAULT_TMAX_BUFFER_MS;
        final BidRequest modifiedRequest = modifyBidRequest(request, publisherId, bufferMs, modifiedImps);
        return Result.withValue(BidderUtil.defaultRequest(modifiedRequest, endpointUrl, mapper));
    }

    private ExtImpOcm parseImpExt(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final ExtImpOcm extImpOcm;
        try {
            extImpOcm = impExt != null
                    ? mapper.mapper().convertValue(impExt, OCM_EXT_TYPE_REFERENCE).getBidder()
                    : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }

        if (extImpOcm == null) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }
        return extImpOcm;
    }

    /**
     * The params schema already enforces presence of both params; the blank checks here cover
     * values the schema accepts but the OCM endpoint cannot resolve.
     */
    private static void validateImpExt(ExtImpOcm extImpOcm, String impId) {
        if (StringUtils.isBlank(extImpOcm.getPublisherId())) {
            throw new PreBidException("imp %s: publisherId is required and must not be blank".formatted(impId));
        }
        if (StringUtils.isBlank(extImpOcm.getPlacementId())) {
            throw new PreBidException("imp %s: placementId is required and must not be blank".formatted(impId));
        }
    }

    private static String resolvePublisherId(String publisherId, String impPublisherId, String impId) {
        if (publisherId == null) {
            return impPublisherId;
        }
        if (!publisherId.equals(impPublisherId)) {
            throw new PreBidException(
                    "imp %s: all impressions must share the same publisherId, found \"%s\" and \"%s\""
                            .formatted(impId, impPublisherId, publisherId));
        }
        return publisherId;
    }

    /**
     * tmax is request-level, so the largest buffer any impression asks for wins: it errs toward
     * answering before the host's deadline.
     */
    private static Integer resolveTmaxBufferMs(Integer tmaxBufferMs, Integer impTmaxBufferMs) {
        if (impTmaxBufferMs == null || impTmaxBufferMs < 0) {
            return tmaxBufferMs;
        }
        return tmaxBufferMs == null ? impTmaxBufferMs : Math.max(tmaxBufferMs, impTmaxBufferMs);
    }

    /**
     * Consumes imp.ext.bidder and records placementId as the impression's stored-request id, where
     * the OCM endpoint looks for it. The surrounding ext is merged rather than replaced so that
     * members this adapter knows nothing about survive the round trip.
     */
    private static Imp modifyImp(Imp imp, String placementId) {
        final ObjectNode modifiedImpExt = imp.getExt().deepCopy();
        modifiedImpExt.remove(BIDDER_FIELD);
        getOrCreateObject(modifiedImpExt, PREBID_FIELD)
                .putObject(STORED_REQUEST_FIELD)
                .put(ID_FIELD, placementId);

        return imp.toBuilder().ext(modifiedImpExt).build();
    }

    private static ObjectNode getOrCreateObject(ObjectNode parent, String field) {
        final JsonNode child = parent.get(field);
        return child != null && child.isObject() ? (ObjectNode) child : parent.putObject(field);
    }

    /**
     * Writes publisherId to every distribution channel present rather than to just one: the OCM
     * endpoint resolves its account from whichever channel it ranks first, so each one must name the
     * OCM publisher.
     */
    private static BidRequest modifyBidRequest(BidRequest request,
                                               String publisherId,
                                               long tmaxBufferMs,
                                               List<Imp> modifiedImps) {

        return request.toBuilder()
                .imp(modifiedImps)
                .user(modifyUser(request.getUser()))
                .tmax(reduceTmax(request.getTmax(), tmaxBufferMs))
                .ext(withoutHostOnlyData(request.getExt()))
                .site(modifySite(request.getSite(), publisherId))
                .app(modifyApp(request.getApp(), publisherId))
                .dooh(modifyDooh(request.getDooh(), publisherId))
                .build();
    }

    /**
     * Moves the OCM buyer UID into user.id. The host resolved user.buyeruid for OCM, so it is the OCM
     * exchange's own identifier for the user. Left in buyeruid, the OCM endpoint would hand it to every
     * bidder in the stored placement as that bidder's own identifier, since a set buyeruid takes
     * precedence there over every other UID source.
     */
    private static User modifyUser(User user) {
        final String buyeruid = ObjectUtil.getIfNotNull(user, User::getBuyeruid);
        if (StringUtils.isBlank(buyeruid)) {
            return user;
        }

        return user.toBuilder().id(buyeruid).buyeruid(null).build();
    }

    /**
     * Reduces tmax by bufferMs. The receiving Prebid Server gives its own bidders the full tmax it is
     * sent, so forwarding the host's value unchanged would land the response after the host's deadline
     * by the round trip plus the downstream overhead. The result never exceeds the original tmax and,
     * as long as the original allows it, never drops below the buffer, so a tight budget is not cut to
     * nothing.
     * <p>
     * An absent or non-positive tmax means the request set none; it is forwarded unchanged so the
     * receiving Prebid Server applies its own default.
     */
    private static Long reduceTmax(Long tmax, long bufferMs) {
        if (tmax == null || tmax <= 0) {
            return tmax;
        }
        return Math.min(Math.max(tmax - bufferMs, bufferMs), tmax);
    }

    /**
     * Drops request-level data that only the host can act on, so the OCM endpoint, itself a Prebid
     * Server, does not act on it a second time:
     * <ul>
     * <li>storedrequest and profiles were resolved and merged into this request by the host. Forwarded,
     * the OCM endpoint would look the host's IDs up under the OCM account instead, and an ID that does
     * not resolve there rejects the whole downstream auction — before any impression placement can be
     * used.</li>
     * <li>bidderparams holds only this adapter's global params, already merged into the impressions.
     * Forwarded, the OCM endpoint would merge them into every stored impression, re-adding the OCM
     * bidder this adapter has just removed.</li>
     * <li>bidadjustmentfactors and bidadjustments hold only this adapter's adjustments, which the host
     * applies to the bids it gets back. They are keyed by the requested bidder name, which may be a
     * request-defined alias the OCM endpoint does not know, and an unknown bidder in
     * bidadjustmentfactors rejects the whole downstream auction.</li>
     * </ul>
     * Everything else in the extension is left untouched.
     */
    private static ExtRequest withoutHostOnlyData(ExtRequest requestExt) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(requestExt, ExtRequest::getPrebid);
        if (prebid == null || !hasHostOnlyData(prebid)) {
            return requestExt;
        }

        final ExtRequest modifiedRequestExt = ExtRequest.of(prebid.toBuilder()
                .storedrequest(null)
                .profiles(null)
                .bidderparams(null)
                .bidadjustmentfactors(null)
                .bidadjustments(null)
                .build());
        modifiedRequestExt.addProperties(requestExt.getProperties());
        return modifiedRequestExt;
    }

    private static boolean hasHostOnlyData(ExtRequestPrebid prebid) {
        return prebid.getStoredrequest() != null
                || prebid.getProfiles() != null
                || prebid.getBidderparams() != null
                || prebid.getBidadjustmentfactors() != null
                || prebid.getBidadjustments() != null;
    }

    private static Site modifySite(Site site, String publisherId) {
        return site != null
                ? site.toBuilder().publisher(modifyPublisher(site.getPublisher(), publisherId)).build()
                : null;
    }

    private static App modifyApp(App app, String publisherId) {
        return app != null
                ? app.toBuilder().publisher(modifyPublisher(app.getPublisher(), publisherId)).build()
                : null;
    }

    private static Dooh modifyDooh(Dooh dooh, String publisherId) {
        return dooh != null
                ? dooh.toBuilder().publisher(modifyPublisher(dooh.getPublisher(), publisherId)).build()
                : null;
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(publisherId)
                .ext(withoutParentAccount(ObjectUtil.getIfNotNull(publisher, Publisher::getExt)))
                .build();
    }

    /**
     * Drops the originating host's parent account, which is meaningless to the OCM endpoint, while
     * retaining every other publisher extension member.
     */
    private static ExtPublisher withoutParentAccount(ExtPublisher publisherExt) {
        if (publisherExt == null || publisherExt.getPrebid() == null) {
            return publisherExt;
        }

        final Map<String, JsonNode> properties = publisherExt.getProperties();
        if (properties.isEmpty()) {
            return null;
        }

        final ExtPublisher modifiedPublisherExt = ExtPublisher.empty();
        modifiedPublisherExt.addProperties(properties);
        return modifiedPublisherExt;
    }

    /**
     * Bids whose media type cannot be resolved are skipped and reported, so one malformed bid does
     * not discard the rest of the seat.
     */
    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final String body = httpCall.getResponse().getBody();
        if (StringUtils.isBlank(body)) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(body, BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bids = extractBids(bidResponse, errors);
            return Result.of(bids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final String currency = StringUtils.defaultIfBlank(bidResponse.getCur(), DEFAULT_CURRENCY);
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final ExtBidPrebid extBidPrebid = parseBidExtPrebid(bid);

        final BidType bidType;
        try {
            bidType = resolveBidType(bid, extBidPrebid);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.builder()
                .bid(bid)
                .type(bidType)
                .bidCurrency(currency)
                .videoInfo(videoInfo(bidType, extBidPrebid))
                .build();
    }

    private ExtBidPrebid parseBidExtPrebid(Bid bid) {
        final ObjectNode bidExt = bid.getExt();
        final JsonNode prebidNode = bidExt != null ? bidExt.get(PREBID_FIELD) : null;
        if (prebidNode == null || !prebidNode.isObject()) {
            return null;
        }

        try {
            return mapper.mapper().treeToValue(prebidNode, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Resolves the media type from bid.mtype, which is authoritative whenever it is set.
     * <p>
     * Not every upstream populates it: a Prebid Server passes through whatever mtype its winning
     * adapter set and reports the type it resolved in bid.ext.prebid.type instead, so treating an
     * absent mtype as fatal would discard perfectly good bids. A set-but-unsupported mtype is
     * rejected outright and never allowed to fall through to the extension, which would otherwise
     * let an ext of "banner" reclassify an audio bid.
     */
    private static BidType resolveBidType(Bid bid, ExtBidPrebid extBidPrebid) {
        final Integer mtype = bid.getMtype();
        if (mtype == null || mtype == 0) {
            return Optional.ofNullable(extBidPrebid)
                    .map(ExtBidPrebid::getType)
                    .filter(SUPPORTED_BID_TYPES::contains)
                    .orElseThrow(() -> new PreBidException(
                            "unable to determine media type for bid \"%s\": no mtype and no ext.prebid.type"
                                    .formatted(bid.getId())));
        }

        return switch (mtype) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "unsupported mtype %d for bid \"%s\"".formatted(mtype, bid.getId()));
        };
    }

    /**
     * Returns the targeting metadata supplied by the receiving Prebid Server for video bids.
     * Invalid, unrelated and non-video bid extensions are ignored.
     */
    private static ExtBidPrebidVideo videoInfo(BidType bidType, ExtBidPrebid extBidPrebid) {
        return bidType == BidType.video
                ? ObjectUtil.getIfNotNull(extBidPrebid, ExtBidPrebid::getVideo)
                : null;
    }
}
