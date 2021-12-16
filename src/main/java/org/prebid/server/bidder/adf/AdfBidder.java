package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdfBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdf>> ADF_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdfBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpAdf adfImp = parseExt(imp);
                final Imp modifiedImp = imp.toBuilder().tagid(adfImp.getMid()).build();
                modifiedImps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final HttpRequest<BidRequest> httpRequest = makeRequest(bidRequest, modifiedImps);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpAdf parseExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADF_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, List<Imp> imps) {
        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(imps).build();

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
                .collect(Collectors.toList());
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency, List<BidderError> errors) {
        final JsonNode prebidNode = ObjectUtil.getIfNotNull(bid.getExt(), node -> node.get("prebid"));
        final JsonNode typeNode = ObjectUtil.getIfNotNull(prebidNode, node -> node.get("type"));
        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException e) {
            addMediaTypeParseError(errors, bid.getImpid());
            return null;
        }

        if (bidType == null) {
            addMediaTypeParseError(errors, bid.getImpid());
            return null;
        }

        return BidderBid.of(bid, bidType, bidCurrency);
    }

    private void addMediaTypeParseError(List<BidderError> errors, String impId) {
        final String errorMessage = String.format("Failed to parse impression %s mediatype", impId);
        errors.add(BidderError.badServerResponse(errorMessage));
    }
}
