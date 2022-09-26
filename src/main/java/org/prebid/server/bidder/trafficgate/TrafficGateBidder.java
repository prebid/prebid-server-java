package org.prebid.server.bidder.trafficgate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.proto.openrtb.ext.request.trafficgate.ExtImpTrafficGate;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TrafficGateBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTrafficGate>> TAPX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String SUBDOMAIN_MACRO = "{{subdomain}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TrafficGateBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        ExtImpTrafficGate extImpTrafficGate;
        for (Imp imp : request.getImp()) {
            try {
                extImpTrafficGate = parseImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            httpRequests.add(createSingleRequest(extImpTrafficGate, request));
        }
        return Result.withValues(httpRequests);
    }

    private HttpRequest<BidRequest> createSingleRequest(ExtImpTrafficGate extImpTrafficGate, BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveHost(extImpTrafficGate))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private String resolveHost(ExtImpTrafficGate extImpTrafficGate) {
        return endpointUrl.replace(SUBDOMAIN_MACRO, extImpTrafficGate.getHost());
    }

    private ExtImpTrafficGate parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TAPX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getExt()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(ObjectNode bidExt) {
        return Optional.ofNullable(bidExt)
                .map(ext -> ext.get("prebid"))
                .map(prebid -> prebid.get("type"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(BidType::fromString)
                .orElse(BidType.banner);
    }
}
