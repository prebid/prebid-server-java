package org.prebid.server.bidder.globalsun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.globalsun.model.GlobalsunImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.globalsun.ExtImpGlobalsun;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GlobalsunBidder implements Bidder<BidRequest> {

    private static final String TYPE_PUBLISHER = "publisher";

    private static final TypeReference<ExtPrebid<?, ExtImpGlobalsun>> GLOBALSUN_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GlobalsunBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> outgoingRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpGlobalsun extImpGlobalsun;
            try {
                extImpGlobalsun = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            outgoingRequests.add(createSingleRequest(modifyImp(imp, extImpGlobalsun), request));
        }

        return Result.withValues(outgoingRequests);
    }

    private ExtImpGlobalsun parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), GLOBALSUN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    private Imp modifyImp(Imp imp, ExtImpGlobalsun extImpAndGlobalsun) {
        final GlobalsunImpExtBidder globalsunImpExtBidder = resolveOutgoingImpExt(extImpAndGlobalsun);
        final ObjectNode modifiedImpExtBidder = mapper.mapper().createObjectNode();

        modifiedImpExtBidder.set("bidder", mapper.mapper().valueToTree(globalsunImpExtBidder));

        return imp.toBuilder()
                .ext(modifiedImpExtBidder)
                .build();
    }

    private static GlobalsunImpExtBidder resolveOutgoingImpExt(ExtImpGlobalsun extImpAndGlobalsun) {
        return GlobalsunImpExtBidder.of(TYPE_PUBLISHER, extImpAndGlobalsun.getPlacementId());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                break;
            }
        }

        throw new PreBidException("Failed to find impression for ID: '%s'".formatted(impId));
    }
}
