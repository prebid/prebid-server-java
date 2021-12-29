package org.prebid.server.bidder.adman;

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
import org.prebid.server.proto.openrtb.ext.request.adman.ExtImpAdman;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdmanBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdman>> ADMAN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdmanBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        final ExtImpAdman extImpAdman;
        final BidRequest modifiedBidRequest;

        try {
            extImpAdman = parseImpExt(firstImp);
            modifiedBidRequest = modifyRequest(request, extImpAdman.getTagId());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return makeRequest(modifiedBidRequest);
    }

    private Result<List<HttpRequest<BidRequest>>> makeRequest(BidRequest modifiedBidRequest) {
        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedBidRequest)
                .body(mapper.encodeToBytes(modifiedBidRequest))
                .build());
    }

    private BidRequest modifyRequest(BidRequest request, String tagId) {
        return request.toBuilder()
                .imp(modifyImps(request.getImp(), tagId))
                .build();
    }

    private List<Imp> modifyImps(List<Imp> imps, String tagId) {
        List<Imp> modifyImps = new ArrayList<>(imps);
        modifyImps.set(0, modifyImp(imps.get(0), tagId));
        return modifyImps;
    }

    private Imp modifyImp(Imp imp, String tagId) {
        return imp.toBuilder()
                .tagid(tagId)
                .build();
    }

    private ExtImpAdman parseImpExt(Imp imp) {
        ExtImpAdman extImpAdman;
        try {
            extImpAdman = mapper.mapper().convertValue(imp.getExt(), ADMAN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Imp.ext could not be parsed");
        }
        if (extImpAdman == null) {
            throw new PreBidException("ext.bidder not provided");
        }
        return extImpAdman;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors, bidRequest);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors, BidRequest bidRequest) {
        if (bidRequest == null || bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors, bidRequest);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bidResponse.getCur(), bidRequest, bid, errors))
                .collect(Collectors.toList());
    }

    private BidderBid resolveBidderBid(String currency, BidRequest bidRequest, Bid bid, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), bidRequest.getImp());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(String.format("Failed to find impression : %s", impId));
    }

}
