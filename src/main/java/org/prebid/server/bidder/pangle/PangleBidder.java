package org.prebid.server.bidder.pangle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pangle.model.BidExt;
import org.prebid.server.bidder.pangle.model.NetworkIds;
import org.prebid.server.bidder.pangle.model.PangleBidExt;
import org.prebid.server.bidder.pangle.model.WrappedImpExtBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.pangle.ExtImpPangle;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pangle {@link Bidder} implementation.
 */
public class PangleBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PangleBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final WrappedImpExtBidder extBidder = parseImpExt(imp);
                final ExtImpPangle extImpPangle = extBidder.getBidder();
                final Integer adType = resolveAdType(imp, extBidder);
                final Imp modifiedImp = modifyImp(imp, adType, extBidder, extImpPangle);

                requests.add(createRequest(request, modifiedImp, extBidder.getBidder().getToken()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private WrappedImpExtBidder parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), WrappedImpExtBidder.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("failed unmarshalling imp ext (err)%s", e.getMessage()));
        }
    }

    private static int resolveAdType(Imp imp, WrappedImpExtBidder extBidder) {
        if (imp.getVideo() != null) {
            final ExtImpPrebid extPrebid = extBidder != null ? extBidder.getPrebid() : null;
            final Integer isRewardedInventory = extPrebid != null ? extPrebid.getIsRewardedInventory() : null;
            if (Objects.equals(isRewardedInventory, 1)) {
                return 7;
            }

            if (Objects.equals(imp.getInstl(), 1)) {
                return 8;
            }
        }

        if (imp.getBanner() != null) {
            if (Objects.equals(imp.getInstl(), 1)) {
                return 2;
            } else {
                return 1;
            }
        }
        if (imp.getXNative() != null && StringUtils.isNotBlank(imp.getXNative().getRequest())) {
            return 5;
        }

        throw new PreBidException("not a supported adtype");
    }

    private Imp modifyImp(Imp imp, Integer adType, WrappedImpExtBidder extBidder, ExtImpPangle bidderImpExt) {
        final NetworkIds modifiedNetworkIds = getNetworkIds(bidderImpExt);

        final WrappedImpExtBidder updatedImpExt = extBidder.toBuilder()
                .adType(adType)
                .isPrebid(true)
                .networkids(modifiedNetworkIds == null ? extBidder.getNetworkids() : modifiedNetworkIds)
                .build();

        return imp.toBuilder()
                .ext(mapper.mapper().convertValue(updatedImpExt, ObjectNode.class))
                .build();
    }

    private static NetworkIds getNetworkIds(ExtImpPangle bidderImpExt) {
        if (bidderImpExt != null) {
            final String appid = bidderImpExt.getAppid();
            final String placementid = bidderImpExt.getPlacementid();

            if (StringUtils.isNotEmpty(appid) && StringUtils.isNotEmpty(placementid)) {
                return NetworkIds.of(appid, placementid);
            } else if (StringUtils.isNotEmpty(appid) || StringUtils.isNotEmpty(placementid)) {
                throw new PreBidException("only one of appid or placementid is provided");
            }
        }

        return null;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, Imp imp, String token) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders(token))
                .payload(outgoingRequest)
                .body(mapper.encode(outgoingRequest))
                .build();
    }

    private static MultiMap makeHeaders(String token) {
        return HttpUtil.headers()
                .add("TOKEN", token);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> createBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid createBid(Bid bid, String currency, List<BidderError> errors) {
        final Integer adType;
        try {
            adType = getAdTypeFromBidExt(bid);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        final BidType bidType;
        switch (adType) {
            case 1:
            case 2:
                bidType = BidType.banner;
                break;
            case 5:
                bidType = BidType.xNative;
                break;
            case 7:
            case 8:
                bidType = BidType.video;
                break;
            default:
                errors.add(BidderError.badServerResponse("unrecognized adtype in response"));
                return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private Integer getAdTypeFromBidExt(Bid bid) {
        if (bid == null) {
            throw new PreBidException("the bid request object is not present");
        }
        final PangleBidExt bidExt;
        try {
            bidExt = mapper.mapper().convertValue(bid.getExt(), PangleBidExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid bid ext");
        }

        final BidExt pangleBidExt = bidExt != null ? bidExt.getPangle() : null;
        final Integer adType = pangleBidExt != null ? pangleBidExt.getAdType() : null;
        if (adType == null) {
            throw new PreBidException("missing pangleExt/adtype in bid ext");
        }
        return adType;
    }
}
