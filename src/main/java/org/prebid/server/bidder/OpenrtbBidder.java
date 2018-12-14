package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class OpenrtbBidder<T> implements Bidder<BidRequest> {

    private final String endpointUrl;

    protected OpenrtbBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    // hook for getting a bidder-specific type reference,
    // which is required for parsing bidrequest.imp[i].ext.bidder
    protected abstract TypeReference<ExtPrebid<?, T>> getTypeReference();

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            validateRequest(bidRequest);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final Map<T, List<Imp>> extToImps = new LinkedHashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final T impExt = parseAndValidateImpExt(imp, getTypeReference());
                extToImps.putIfAbsent(impExt, new ArrayList<>());
                extToImps.get(impExt).add(modifyImp(imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        final List<Imp> modifiedImps = extToImps.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impression in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }
        final MultiMap headers = makeHeaders(bidRequest);

        return Result.of(createHttpRequests(extToImps, modifiedImps, bidRequest, headers, errors), errors);
    }

    // hook for bidder-specific request validation if any is required
    protected void validateRequest(BidRequest bidRequest) throws PreBidException {
    }

    //hook for bidder-specific imp validation if any is required
    protected void validateImp(Imp imp) {
    }

    private T parseAndValidateImpExt(Imp imp, TypeReference typeReference) {
        final T impExt;
        try {
            impExt = Json.mapper.<ExtPrebid<?, T>>convertValue(imp.getExt(), typeReference).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        validateImpExt(impExt, imp);

        return impExt;
    }

    //hook for bidder-specific imp.ext validation if any is required
    protected void validateImpExt(T impExt, Imp imp) {
    }

    // hook for impression changes.
    // To be defined by client as most bidders apply some changes to outgoing impressions.
    // If no changes are made or to be made later on - just return an incoming impression.
    protected abstract Imp modifyImp(Imp imp, T impExt);

    // hook for request headers changes.
    // By default - returns basic headers
    protected MultiMap makeHeaders(BidRequest bidRequest) {
        return BidderUtil.headers();
    }

    // hook for bidder-specific http requests creation logic.
    // By default - a one-to-one implementation (modifies incoming request and forwards it to bidder)
    // Can be modified to handle one-to-many scenarios (creating multiple requests from single incoming request)
    protected List<HttpRequest<BidRequest>> createHttpRequests(Map<T, List<Imp>> extToImps, List<Imp> modifiedImps,
                                                               BidRequest bidRequest, MultiMap headers,
                                                               List<BidderError> errors) {
        return Collections.singletonList(
                makeRequest(new ArrayList<>(extToImps.keySet()), modifiedImps, bidRequest, headers, errors));
    }

    private HttpRequest<BidRequest> makeRequest(List<T> impExts, List<Imp> modifiedImps, BidRequest bidRequest,
                                                MultiMap headers, List<BidderError> errors) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        requestBuilder.imp(modifiedImps);

        try {
            modifyRequest(bidRequest, requestBuilder, impExts, modifiedImps);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = Json.encode(outgoingRequest);

        final String endpoint = makeEndpoint(impExts, endpointUrl, errors);

        return HttpRequest.of(HttpMethod.POST, endpoint, body, headers, outgoingRequest);
    }

    // hook for any request changes other than Imps (although Impressions can be modified as well)
    // Default - no changes (other than imps, prior this method call)
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<T> impExts, List<Imp> modifiedImps) {
    }

    // hook for request endpoint url changes.
    // By default - url not changed
    protected String makeEndpoint(List<T> impExts, String endpointUrl, List<BidderError> errors) {
        return endpointUrl;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(bid, bidRequest.getImp()))
                .collect(Collectors.toList());
    }

    // hook for overriding default bid creation logic if any is required
    protected BidderBid createBidderBid(Bid bid, List<Imp> imps) {
        return BidderBid.of(bid, getBidType(bid.getImpid(), imps), getBidCurrency());
    }

    // hook for resolving bidder-specific bid type.
    // Default - banner, if imp has video - video
    protected BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    // hook for defining a bid currency.
    // Default - USD.
    protected String getBidCurrency() {
        return "USD";
    }

    @Override
    public final Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
