package org.prebid.server.bidder.floxis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.request.floxis.ExtImpFloxis;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FloxisBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFloxis>> FLOXIS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String HOST_MACRO = "Host";
    private static final String SEAT_MACRO = "SeatId";

    private static final String DEFAULT_REGION = "us-e";
    private static final String DEFAULT_PARTNER = "floxis";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public FloxisBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> impsByTarget = new LinkedHashMap<>();
        final Map<String, ExtImpFloxis> extsByTarget = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            final ExtImpFloxis impExt;
            try {
                impExt = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final String target = impExt.getSeat() + "|"
                    + resolveBidHost(impExt.getRegion(), impExt.getPartner());
            impsByTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(imp);
            extsByTarget.putIfAbsent(target, impExt);
        }

        final List<HttpRequest<BidRequest>> httpRequests = impsByTarget.entrySet().stream()
                .map(entry -> BidderUtil.defaultRequest(
                        request.toBuilder().imp(entry.getValue()).build(),
                        resolveUrl(extsByTarget.get(entry.getKey())),
                        mapper))
                .toList();

        return Result.of(httpRequests, errors);
    }

    private ExtImpFloxis parseImpExt(Imp imp) {
        final ExtImpFloxis impExt;
        try {
            impExt = mapper.mapper().convertValue(imp.getExt(), FLOXIS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext.bidder for imp %s: %s".formatted(imp.getId(), e.getMessage()));
        }

        return ExtImpFloxis.of(
                impExt.getSeat(),
                StringUtils.isBlank(impExt.getRegion()) ? DEFAULT_REGION : impExt.getRegion(),
                StringUtils.isBlank(impExt.getPartner()) ? DEFAULT_PARTNER : impExt.getPartner());
    }

    private String resolveUrl(ExtImpFloxis extImp) {
        return endpointUrl
                .replaceMacro(HOST_MACRO, resolveBidHost(extImp.getRegion(), extImp.getPartner()))
                .replaceMacro(SEAT_MACRO, extImp.getSeat())
                .expand();
    }

    private static String resolveBidHost(String region, String partner) {
        return partner.equals(DEFAULT_PARTNER) ? region : partner + "-" + region;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, bidRequest.getImp(), errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<Imp> imps, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, imps, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getMediaTypeForBid(imps, bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getMediaTypeForBid(List<Imp> imps, Bid bid) {
        final Integer mtype = bid.getMtype();
        if (mtype != null && mtype != 0) {
            return switch (mtype) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 3 -> BidType.audio;
                case 4 -> BidType.xNative;
                default -> throw new PreBidException(
                        "unsupported bid.mtype %d for impression %s".formatted(mtype, bid.getImpid()));
            };
        }

        final Imp imp = imps.stream()
                .filter(currentImp -> Objects.equals(currentImp.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> new PreBidException(
                        "unable to find impression %s for bid".formatted(bid.getImpid())));

        if (countFormats(imp) != 1) {
            throw new PreBidException(
                    "unable to resolve a single media type for impression %s; set bid.mtype"
                            .formatted(bid.getImpid()));
        }

        if (imp.getBanner() != null) {
            return BidType.banner;
        } else if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getAudio() != null) {
            return BidType.audio;
        } else {
            return BidType.xNative;
        }
    }

    private static int countFormats(Imp imp) {
        int formats = 0;
        if (imp.getBanner() != null) {
            formats++;
        }
        if (imp.getVideo() != null) {
            formats++;
        }
        if (imp.getAudio() != null) {
            formats++;
        }
        if (imp.getXNative() != null) {
            formats++;
        }
        return formats;
    }
}
