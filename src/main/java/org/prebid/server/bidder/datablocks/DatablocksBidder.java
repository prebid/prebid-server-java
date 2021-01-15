package org.prebid.server.bidder.datablocks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.datablocks.ExtImpDatablocks;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DatablocksBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpDatablocks>> DATABLOCKS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpDatablocks>>() {
            };

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public DatablocksBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Map<ExtImpDatablocks, List<Imp>> extToImps = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpDatablocks extImpDatablocks = parseAndValidateImpExt(imp.getExt());
                extToImps.computeIfAbsent(extImpDatablocks, ext -> new ArrayList<>()).add(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> httpRequests = extToImps.entrySet().stream()
                .map(entry -> makeHttpRequest(entry, bidRequest))
                .collect(Collectors.toList());

        return Result.withValues(httpRequests);
    }

    private ExtImpDatablocks parseAndValidateImpExt(ObjectNode extNode) {
        final ExtImpDatablocks extImpDatablocks;
        try {
            extImpDatablocks = mapper.mapper().convertValue(extNode, DATABLOCKS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        final Integer sourceId = extImpDatablocks.getSourceId();
        if (sourceId == null || sourceId < 1) {
            throw new PreBidException("Invalid/Missing SourceId");
        }

        if (StringUtils.isBlank(extImpDatablocks.getHost())) {
            throw new PreBidException("Invalid/Missing Host");
        }

        return extImpDatablocks;
    }

    private HttpRequest<BidRequest> makeHttpRequest(Map.Entry<ExtImpDatablocks, List<Imp>> extToImps,
                                                    BidRequest bidRequest) {
        final ExtImpDatablocks extImpDatablocks = extToImps.getKey();
        final String uri = endpointTemplate
                .replace("{{Host}}", extImpDatablocks.getHost())
                .replace("{{SourceId}}", extImpDatablocks.getSourceId().toString());

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(extToImps.getValue()).build();
        final String body = mapper.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(uri)
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> requestImps) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getMediaType(bid.getImpid(), requestImps), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getMediaType(String impId, List<Imp> requestImps) {
        for (Imp imp : requestImps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                return BidType.banner;
            }
        }
        return BidType.banner;
    }
}
