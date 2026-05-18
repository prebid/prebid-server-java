package org.prebid.server.bidder.hypelab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.hypelab.ExtImpHypeLab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class HypeLabBidder implements Bidder<BidRequest> {

    private static final String DISPLAY_MANAGER = "HypeLab Prebid Server";
    private static final String SOURCE = "prebid-server";
    private static final String PROVIDER_VERSION_PREFIX = "prebid-server@";
    private static final String UNKNOWN_VERSION = "unknown";
    private static final TypeReference<ExtPrebid<?, ExtImpHypeLab>> HYPELAB_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final PrebidVersionProvider prebidVersionProvider;

    public HypeLabBidder(String endpointUrl, JacksonMapper mapper, PrebidVersionProvider prebidVersionProvider) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpHypeLab extImp = parseImpExt(imp);
                validImps.add(makeOutgoingImp(imp, extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(validImps)
                .ext(makeOutgoingRequestExt(request.getExt()))
                .build();

        return Result.of(Collections.singletonList(BidderUtil.defaultRequest(outgoingRequest, headers(), endpointUrl,
                        mapper)),
                errors);
    }

    private Imp makeOutgoingImp(Imp imp, ExtImpHypeLab extImp) {
        final String pbsVersion = pbsVersion();

        return imp.toBuilder()
                .tagid(extImp.getPlacementSlug())
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(pbsVersion)
                .ext(mapper.mapper().valueToTree(ExtPrebid.of(null, extImp)))
                .build();
    }

    private ExtImpHypeLab parseImpExt(Imp imp) {
        if (imp.getExt() == null) {
            throw new PreBidException("imp %s: unable to unmarshal ext".formatted(imp.getId()));
        }

        final ExtImpHypeLab extImp;
        try {
            final ExtPrebid<?, ExtImpHypeLab> extPrebid =
                    mapper.mapper().convertValue(imp.getExt(), HYPELAB_EXT_TYPE_REFERENCE);
            extImp = extPrebid != null ? extPrebid.getBidder() : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp %s: unable to unmarshal ext.bidder".formatted(imp.getId()), e);
        }

        if (extImp == null) {
            throw new PreBidException("imp %s: unable to unmarshal ext.bidder".formatted(imp.getId()));
        }

        if (StringUtils.isBlank(extImp.getPropertySlug()) || StringUtils.isBlank(extImp.getPlacementSlug())) {
            throw new PreBidException("imp %s: property_slug and placement_slug are required".formatted(imp.getId()));
        }

        return extImp;
    }

    private ExtRequest makeOutgoingRequestExt(ExtRequest ext) {
        final ExtRequest outgoingExt = ext != null ? ExtRequest.of(ext.getPrebid()) : ExtRequest.empty();
        if (ext != null) {
            mapper.fillExtension(outgoingExt, ext.getProperties());
        }

        return mapper.fillExtension(outgoingExt, Map.of(
                "source", SOURCE,
                "provider_version", PROVIDER_VERSION_PREFIX + pbsVersion()));
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.6");
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
    }

    private static List<BidderBid> extractBids(BidRequest request, BidResponse response, List<BidderError> errors) {
        if (response == null || CollectionUtils.isEmpty(response.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impIdToImp = request.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp));

        return response.getSeatbid().stream()
                .filter(Objects::nonNull)
                .flatMap(seatBid -> CollectionUtils.emptyIfNull(seatBid.getBid()).stream()
                        .filter(Objects::nonNull)
                        .map(bid -> makeBidderBid(bid, seatBid.getSeat(), impIdToImp, response.getCur(), errors))
                        .filter(Objects::nonNull))
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String seat, Map<String, Imp> impIdToImp, String currency,
                                           List<BidderError> errors) {

        try {
            return BidderBid.of(bid, resolveBidType(bid, impIdToImp), seat, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid, Map<String, Imp> impIdToImp) {
        return bidTypeFromMtype(bid.getMtype())
                .or(() -> bidTypeFromExt(bid))
                .or(() -> bidTypeFromAdm(bid.getAdm()))
                .or(() -> bidTypeFromImp(bid, impIdToImp))
                .orElseThrow(() -> new PreBidException("unable to determine media type for bid %s on imp %s"
                        .formatted(bid.getId(), bid.getImpid())));
    }

    private static Optional<BidType> bidTypeFromMtype(Integer mtype) {
        return Optional.ofNullable(switch (mtype) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> null;
        });
    }

    private static Optional<BidType> bidTypeFromExt(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("hypelab"))
                .map(hypelab -> hypelab.get("creative_type"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(creativeType -> switch (creativeType) {
                    case "display" -> BidType.banner;
                    case "video" -> BidType.video;
                    case "native" -> BidType.xNative;
                    default -> null;
                });
    }

    private static Optional<BidType> bidTypeFromAdm(String adm) {
        return StringUtils.startsWith(StringUtils.trimToEmpty(adm), "<VAST")
                ? Optional.of(BidType.video)
                : Optional.empty();
    }

    private static Optional<BidType> bidTypeFromImp(Bid bid, Map<String, Imp> impIdToImp) {
        final Imp imp = impIdToImp.get(bid.getImpid());
        if (imp == null) {
            return Optional.empty();
        }

        BidType bidType = null;
        int mediaTypeCount = 0;
        if (imp.getBanner() != null) {
            bidType = BidType.banner;
            mediaTypeCount++;
        }
        if (imp.getVideo() != null) {
            bidType = BidType.video;
            mediaTypeCount++;
        }
        if (imp.getXNative() != null) {
            bidType = BidType.xNative;
            mediaTypeCount++;
        }

        return mediaTypeCount == 1 ? Optional.of(bidType) : Optional.empty();
    }

    private String pbsVersion() {
        return StringUtils.defaultIfBlank(prebidVersionProvider.getNameVersionRecord(), UNKNOWN_VERSION);
    }
}
