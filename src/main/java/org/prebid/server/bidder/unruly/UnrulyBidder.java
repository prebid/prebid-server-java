package org.prebid.server.bidder.unruly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.unruly.proto.ImpExtUnruly;
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
            new TypeReference<ExtPrebid<?, ExtImpUnruly>>() {
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
            }
        }

        final List<HttpRequest<BidRequest>> outgoingRequests = modifiedImps.stream()
                .map(imp -> createSingleRequest(imp, request, endpointUrl))
                .collect(Collectors.toList());

        return Result.of(outgoingRequests, errors);
    }

    private ExtImpUnruly parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), UNRULY_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp modifyImp(Imp imp, ExtImpUnruly extImpUnruly) {
        final Imp.ImpBuilder modifiedImp = imp.toBuilder();

        try {
            modifiedImp.ext(mapper.mapper().valueToTree(ImpExtUnruly.of(extImpUnruly)));
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        return modifiedImp.build();
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp modifiedImp, BidRequest request,
                                                        String endpointUrl) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(modifiedImp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(getHeaders())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    private static MultiMap getHeaders() {
        return HttpUtil.headers()
                .add("X-Unruly-Origin", "Prebid-Server");
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return BidType.video;
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
