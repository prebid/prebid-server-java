package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adf.ExtImpAdf;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AdfBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ExtImpAdf>> ADF_EXT_TYPE_REFERENCE =
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
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = bidRequest.getImp().stream()
                .filter(Objects::nonNull)
                .map(imp -> modifyImp(imp, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final HttpRequest<BidRequest> httpRequest = makeRequest(bidRequest, modifiedImps);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private Imp modifyImp(Imp imp, List<BidderError> errors) {
        final ExtPrebid<?, ExtImpAdf> ext;
        try {
            ext = ObjectUtil.getIfNotNull(imp.getExt(), this::parseExt);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }

        final ExtImpAdf adfImp = ObjectUtil.getIfNotNull(ext, ExtPrebid::getBidder);
        if (adfImp == null) {
            final String bidderErrorMessage = String.format("Failed to parse impression %s", imp.getId());
            errors.add(BidderError.badInput(bidderErrorMessage));
            return null;
        }

        return imp.withTagid(adfImp.getMid());
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, List<Imp> imps) {
        final BidRequest outgoingRequest = bidRequest.withImp(imps);

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

        if (bidResponse.getCur() == null) {
            errors.add(BidderError.badServerResponse("Currency is null."));
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
        final ExtPrebid<ExtBidPrebid, ?> ext;
        try {
            ext = ObjectUtil.getIfNotNull(bid.getExt(), this::parseExt);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        final ExtBidPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtPrebid::getPrebid);
        final BidType bidType = ObjectUtil.getIfNotNull(prebid, ExtBidPrebid::getType);
        if (bidType == null) {
            final String bidderErrorMessage = String.format("Failed to parse bid %s mediatype", bid.getImpid());
            errors.add(BidderError.badServerResponse(bidderErrorMessage));
            return null;
        }

        return BidderBid.of(bid, bidType, bidCurrency);
    }

    private ExtPrebid<ExtBidPrebid, ExtImpAdf> parseExt(ObjectNode ext) throws IllegalArgumentException {
        return mapper.mapper().convertValue(ext, ADF_EXT_TYPE_REFERENCE);
    }
}
