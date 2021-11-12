package org.prebid.server.bidder.adf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import lombok.NonNull;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class AdfBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ExtImpAdf>> ADF_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdfBidder(@NonNull String endpointUrl,
                     @NonNull JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = mapper;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = bidRequest.getImp().stream()
                .filter(Objects::nonNull)
                .map(imp -> modifyImp(imp, errors::add))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final HttpRequest<BidRequest> httpRequest = makeRequest(bidRequest, modifiedImps);
        return Result.of(List.of(httpRequest), errors);
    }

    private Imp modifyImp(Imp imp, Consumer<BidderError> onError) {
        final Optional<ExtImpAdf> optionalAdfImp;
        try {
            optionalAdfImp = Optional.ofNullable(imp.getExt())
                    .map(this::parseExt)
                    .map(ExtPrebid::getBidder);
        } catch (IllegalArgumentException e) {
            onError.accept(BidderError.badInput(e.getMessage()));
            return null;
        }

        if (optionalAdfImp.isEmpty()) {
            final String bidderErrorMessage = String.format("Failed to parse impression %s", imp.getId());
            onError.accept(BidderError.badInput(bidderErrorMessage));
            return null;
        }

        return imp.withTagid(optionalAdfImp.orElseThrow().getMid());
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
            final BidResponse bidResponse = parseBidResponse(httpCall);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors::add);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse parseBidResponse(HttpCall<BidRequest> httpCall) throws DecodeException {
        return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, Consumer<BidderError> onError) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        if (bidResponse.getCur() == null) {
            onError.accept(BidderError.badServerResponse("Currency is null."));
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), onError))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid makeBidderBid(Bid bid, String bidCurrency, Consumer<BidderError> onError) {
        final Optional<BidType> optionalBidType;
        try {
            optionalBidType = Optional.ofNullable(bid.getExt())
                    .map(this::parseExt)
                    .map(ExtPrebid::getPrebid)
                    .map(ExtBidPrebid::getType);
        } catch (IllegalArgumentException e) {
            onError.accept(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        if (optionalBidType.isEmpty()) {
            final String bidderErrorMessage = String.format("Failed to parse bid %s mediatype", bid.getImpid());
            onError.accept(BidderError.badServerResponse(bidderErrorMessage));
            return null;
        }

        return BidderBid.of(bid, optionalBidType.orElseThrow(), bidCurrency);
    }

    private ExtPrebid<ExtBidPrebid, ExtImpAdf> parseExt(ObjectNode ext) throws IllegalArgumentException {
        return mapper.mapper().convertValue(ext, ADF_EXT_TYPE_REFERENCE);
    }
}
