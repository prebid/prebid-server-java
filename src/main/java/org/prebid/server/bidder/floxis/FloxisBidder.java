package org.prebid.server.bidder.floxis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.List;
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
        final List<Imp> imps = request.getImp();
        final Imp firstImp = imps.getFirst();

        final ExtImpFloxis firstImpExt;
        try {
            firstImpExt = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        try {
            for (int i = 1; i < imps.size(); i++) {
                final Imp imp = imps.get(i);
                validateImpExt(parseImpExt(imp), firstImpExt, imp.getId(), firstImp.getId());
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveUrl(firstImpExt))
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(request))
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build());
    }

    private ExtImpFloxis parseImpExt(Imp imp) {
        final ExtImpFloxis impExt;
        try {
            impExt = mapper.mapper().convertValue(imp.getExt(), FLOXIS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext.bidder for imp %s: %s".formatted(imp.getId(), e.getMessage()));
        }

        // defaults applied here so imps differing only by an omitted region or partner compare equal
        return ExtImpFloxis.of(
                impExt.getSeat(),
                StringUtils.isBlank(impExt.getRegion()) ? DEFAULT_REGION : impExt.getRegion(),
                StringUtils.isBlank(impExt.getPartner()) ? DEFAULT_PARTNER : impExt.getPartner());
    }

    private static void validateImpExt(ExtImpFloxis impExt,
                                       ExtImpFloxis firstImpExt,
                                       String impId,
                                       String firstImpId) {

        if (!impExt.equals(firstImpExt)) {
            throw new PreBidException(
                    "all impressions must target the same Floxis seat, region and partner; "
                            + "imp %s differs from imp %s".formatted(impId, firstImpId));
        }
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
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        return extractBids(bidResponse, bidRequest);
    }

    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid == null || CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                try {
                    bids.add(BidderBid.of(bid, getMediaTypeForBid(bidRequest.getImp(), bid), bidResponse.getCur()));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }

        return Result.of(bids, errors);
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
