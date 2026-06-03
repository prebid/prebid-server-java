package org.prebid.server.bidder.floxis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FloxisBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFloxis>> FLOXIS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String HOST_MACRO = "{{Host}}";
    private static final String SEAT_MACRO = "{{SeatId}}";

    // Fixed allowlist mapping the bidder's region param to a Floxis RTB host. Routing is
    // never derived from request-supplied hostnames; an unknown or empty region falls back
    // to us-e.
    private static final Map<String, String> REGION_HOSTS = Map.of(
            "us-e", "rtb-us-e.floxis.tech",
            "eu", "rtb-eu.floxis.tech",
            "apac", "rtb-apac.floxis.tech");

    private static final String DEFAULT_REGION = "us-e";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public FloxisBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.withError(BidderError.badInput("no impressions in the bid request"));
        }

        final ExtImpFloxis extImp;
        try {
            extImp = parseImpExt(request.getImp().getFirst());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        // The request body is forwarded unchanged; no caller-owned struct is mutated.
        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveUrl(endpointUrl, extImp))
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(request))
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build());
    }

    private ExtImpFloxis parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FLOXIS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext.bidder for imp %s: %s".formatted(imp.getId(), e.getMessage()));
        }
    }

    private static String resolveHost(String region) {
        final String host = region == null ? null : REGION_HOSTS.get(region);
        return host != null ? host : REGION_HOSTS.get(DEFAULT_REGION);
    }

    private static String resolveUrl(String endpoint, ExtImpFloxis extImp) {
        return endpoint
                .replace(HOST_MACRO, resolveHost(extImp.getRegion()))
                .replace(SEAT_MACRO, HttpUtil.encodeUrl(extImp.getSeat()));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

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

    // Resolves the bid's media type. When bid.mtype (OpenRTB 2.6) is set it is treated as
    // authoritative. When unset, a single-format imp's media type is used; multi-format imps
    // without mtype cannot be disambiguated and surface an error.
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

        for (Imp imp : imps) {
            if (!Objects.equals(imp.getId(), bid.getImpid())) {
                continue;
            }
            int formats = 0;
            BidType resolved = null;
            if (imp.getBanner() != null) {
                formats++;
                resolved = BidType.banner;
            }
            if (imp.getVideo() != null) {
                formats++;
                resolved = BidType.video;
            }
            if (imp.getAudio() != null) {
                formats++;
                resolved = BidType.audio;
            }
            if (imp.getXNative() != null) {
                formats++;
                resolved = BidType.xNative;
            }
            if (formats == 1) {
                return resolved;
            } else if (formats > 1) {
                throw new PreBidException(
                        "bid for multi-format imp %s requires bid.mtype to disambiguate".formatted(bid.getImpid()));
            } else {
                throw new PreBidException(
                        "unable to resolve media type for impression %s".formatted(bid.getImpid()));
            }
        }

        throw new PreBidException("unable to find impression %s for bid".formatted(bid.getImpid()));
    }
}
