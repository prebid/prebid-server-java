package org.prebid.server.bidder.cpmstar;

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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.cpmstar.ExtImpCpmStar;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CpmStarBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpCpmStar>> CPM_STAR_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public CpmStarBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final BidRequest bidRequest = processRequest(request);

            return Result.of(
                    Collections.singletonList(HttpRequest.<BidRequest>builder()
                            .method(HttpMethod.POST)
                            .uri(endpointUrl)
                            .headers(HttpUtil.headers())
                            .body(mapper.encodeToBytes(bidRequest))
                            .payload(request)
                            .build()),
                    Collections.emptyList());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private BidRequest processRequest(BidRequest bidRequest) {
        final List<Imp> validImpList = new ArrayList<>();
        for (final Imp imp : bidRequest.getImp()) {
            ExtImpCpmStar extImpCpmStar = parseImp(imp);
            validImpList.add(createImp(extImpCpmStar, imp));
        }
        return bidRequest.toBuilder().imp(validImpList).build();
    }

    private ExtImpCpmStar parseImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("Only Banner and Video bid-types are supported at this time");
        }
        try {
            return mapper.mapper().convertValue(imp.getExt(), CPM_STAR_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp createImp(ExtImpCpmStar extImpCpmStar, Imp imp) {
        if (extImpCpmStar == null) {
            throw new PreBidException(String.format("imp id=%s: bidder.ext is null", imp.getId()));
        }
        return imp.toBuilder().ext(mapper.mapper().valueToTree(extImpCpmStar)).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidRequest request, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        final List<Bid> responseBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> result = bidsFromResponse(request.getImp(), responseBids, bidResponse.getCur(), errors);
        return Result.of(result, errors);
    }

    private static List<BidderBid> bidsFromResponse(List<Imp> imps, List<Bid> responseBids, String currency,
                                                    List<BidderError> errors) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (Bid bid : responseBids) {
            try {
                bidderBids.add(BidderBid.of(bid, bidType(bid.getImpid(), imps), currency));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(String.format("bid id=%s %s", bid.getId(), e.getMessage())));
            }
        }
        return bidderBids;
    }

    private static BidType bidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(String.format("could not find valid impid=%s", impId));
    }
}
