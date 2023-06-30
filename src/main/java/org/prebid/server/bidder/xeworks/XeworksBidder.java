package org.prebid.server.bidder.xeworks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.xeworks.ExtImpXeworks;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class XeworksBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpXeworks>> XEWORKS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String HOST_MACRO = "{{Host}}";
    private static final String SOURCE_ID_MACRO = "{{SourceId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public XeworksBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpXeworks extImpXeworks = parseImpExt(imp);
                httpRequests.add(BidderUtil.defaultRequest(
                        request,
                        buildEndpointUrl(extImpXeworks),
                        mapper));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpXeworks parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), XEWORKS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Xeworks extension in impression with id: " + imp.getId());
        }
    }

    private String buildEndpointUrl(ExtImpXeworks extImpXeworks) {
        return endpointUrl.replace(HOST_MACRO, extImpXeworks.getEnv())
                .replace(SOURCE_ID_MACRO, extImpXeworks.getPid());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency, List<BidderError> errors) {
        final JsonNode typeNode = Optional.ofNullable(bid.getExt())
                .map(extNode -> extNode.get("prebid"))
                .map(extPrebidNode -> extPrebidNode.get("type"))
                .orElse(null);
        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            addMediaTypeParseError(errors, bid.getId());
            return null;
        }

        if (bidType == null) {
            addMediaTypeParseError(errors, bid.getId());
            return null;
        }

        return BidderBid.of(bid, bidType, bidCurrency);
    }

    private static void addMediaTypeParseError(List<BidderError> errors, String bidId) {
        errors.add(BidderError.badServerResponse(
                "Failed to parse bid.ext.prebid.type for bid.id: '%s'"
                        .formatted(bidId)));
    }
}
