package org.prebid.server.bidder.unruly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.prebid.server.proto.openrtb.ext.request.unruly.ExtImpUnruly;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UnrulyBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpUnruly>> UNRULY_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public UnrulyBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpUnruly extImpUnruly = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, extImpUnruly));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.withErrors(errors);
            }
        }

        final List<HttpRequest<BidRequest>> outgoingRequests = modifiedImps.stream()
                .map(imp -> createSingleRequest(imp, request))
                .collect(Collectors.toList());

        return Result.of(outgoingRequests, errors);
    }

    private ExtImpUnruly parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), UNRULY_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("ext data not provided in imp id=%s. Abort all Request",
                    imp.getId()));
        }
    }

    private Imp modifyImp(Imp imp, ExtImpUnruly extImpUnruly) {
        final ObjectNode modifiedExt = mapper.mapper().createObjectNode();
        modifiedExt.set("bidder", mapper.mapper().convertValue(extImpUnruly, JsonNode.class));
        return imp.toBuilder().ext(modifiedExt).build();
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp modifiedImp, BidRequest request) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(modifiedImp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse,
                                               List<BidderError> errors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse,
                                                    List<BidderError> errors) {
        return bidResponse.getSeatbid()
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidRequest.getImp(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid resolveBidderBid(Bid bid, String currency, List<Imp> imps, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        final List<String> noMatchingImps = new ArrayList<>();

        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else {
                    return BidType.banner;
                }
            } else {
                noMatchingImps.add(imp.getId());
            }
        }

        throw new PreBidException(String
                .format("Bid response imp ID %s not found in bid request containing imps %s",
                        impId, noMatchingImps));
    }
}
