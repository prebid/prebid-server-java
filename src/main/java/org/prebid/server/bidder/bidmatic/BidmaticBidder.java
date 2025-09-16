package org.prebid.server.bidder.bidmatic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import org.prebid.server.proto.openrtb.ext.request.bidmatic.ExtImpBidmatic;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BidmaticBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBidmatic>> EXT_IMP_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BidmaticBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        final Map<Integer, List<Imp>> sourceToImpsMap = new HashMap<>();

        for (Imp imp : request.getImp()) {
            final ExtImpBidmatic extImp;
            try {
                extImp = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final int sourceId;
            try {
                sourceId = Integer.parseInt(extImp.getSourceId());
            } catch (NumberFormatException e) {
                errors.add(BidderError.badInput("Cannot parse sourceId=%s to int".formatted(extImp.getSourceId())));
                continue;
            }

            final Imp modifiedImp = modifyImp(imp, sourceId, extImp);
            sourceToImpsMap.putIfAbsent(sourceId, new ArrayList<>());
            sourceToImpsMap.get(sourceId).add(modifiedImp);
        }

        if (sourceToImpsMap.isEmpty()) {
            return Result.withErrors(errors);
        }

        sourceToImpsMap.forEach((sourceId, imps) -> requests.add(makeHttpRequest(request, sourceId, imps)));
        return Result.of(requests, errors);
    }

    private ExtImpBidmatic parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_IMP_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, Integer sourceId, ExtImpBidmatic extImp) {
        final BidmaticImpExt modifiedExtImp = BidmaticImpExt.of(
                sourceId, extImp.getPlacementId(), extImp.getSiteId(), extImp.getBidFloor());

        return imp.toBuilder()
                .bidfloor(BidderUtil.isValidPrice(extImp.getBidFloor()) ? extImp.getBidFloor() : imp.getBidfloor())
                .ext(mapper.mapper().createObjectNode().set("bidmatic", mapper.mapper().valueToTree(modifiedExtImp)))
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Integer sourceId, List<Imp> imps) {
        final BidRequest modifiedRequest = request.toBuilder().imp(imps).build();
        return BidderUtil.defaultRequest(modifiedRequest, makeUrl(sourceId), mapper);
    }

    private String makeUrl(Integer sourceId) {
        return endpointUrl + "?source=%d".formatted(sourceId);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, impMap, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBid(Bid bid, Map<String, Imp> impMap, String currency, List<BidderError> errors) {
        try {
            final Pair<BidType, Integer> bidType = getBidType(bid, impMap);
            final Bid modifiedBid = bid.toBuilder().mtype(bidType.getRight()).build();
            return BidderBid.of(modifiedBid, bidType.getLeft(), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static Pair<BidType, Integer> getBidType(Bid bid, Map<String, Imp> impIdToImpMap) {
        final Imp imp = impIdToImpMap.get(bid.getImpid());
        if (imp == null) {
            throw new PreBidException("ignoring bid id=%s, request doesn't contain any impression with id=%s"
                    .formatted(bid.getId(), bid.getImpid()));
        }

        if (imp.getBanner() != null) {
            return Pair.of(BidType.banner, 1);
        } else if (imp.getVideo() != null) {
            return Pair.of(BidType.video, 2);
        } else if (imp.getXNative() != null) {
            return Pair.of(BidType.xNative, 4);
        } else if (imp.getAudio() != null) {
            return Pair.of(BidType.audio, 3);
        } else {
            return Pair.of(BidType.banner, 1);
        }
    }
}
