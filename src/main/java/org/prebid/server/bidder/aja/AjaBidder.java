package org.prebid.server.bidder.aja;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.aja.proto.ExtImpAja;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
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

public class AjaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAja>> AJA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAja>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AjaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        final List<String> tagIds = new ArrayList<>();
        final Map<String, Imp> impsByTagID = new HashMap<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpAja extImpAja = parseExtAJA(imp, errors);
            if (extImpAja == null) {
                continue;
            }
            imp = imp.toBuilder()
                    .tagid(extImpAja.getAdSpotID())
                    .ext(null)
                    .build();

            final String tagId = imp.getTagid();
            if (!impsByTagID.containsKey(tagId)) {
                tagIds.add(tagId);
            }
            impsByTagID.put(tagId, imp);
        }

        for (final String tagId : tagIds) {
            final Imp imp = impsByTagID.get(tagId);
            final HttpRequest<BidRequest> singleRequest = createSingleRequest(imp, bidRequest, endpointUrl, errors);
            if (singleRequest == null) {
                continue;
            }
            result.add(singleRequest);
        }

        return Result.of(result, errors);
    }

    private ExtImpAja parseExtAJA(Imp imp, List<BidderError> errors) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), AJA_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badInput(
                    String.format("Failed to unmarshal ext.bidder impID: %s err: %s", imp.getId(), e.getMessage())));
        }
        return null;
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url,
                                                        List<BidderError> errors) {
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        final String body;
        try {
            body = mapper.encode(outgoingRequest);
        } catch (EncodeException e) {
            errors.add(BidderError.badInput(
                    String.format("Failed to unmarshal bidrequest ID: %s err: %s", request.getId(), e.getMessage())));
            return null;
        }

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(HttpUtil.headers())
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
                return Result.of(Collections.emptyList(), Collections.emptyList());
            } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
                return Result.emptyWithError(BidderError.badInput(
                        String.format("Unexpected status code: %d", statusCode)));
            } else {
                return Result.emptyWithError(BidderError.badServerResponse(
                        String.format("Unexpected status code: %d", statusCode)));
            }
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidFromResponse(bidRequest.getImp(), bid, errors, bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private static BidderBid bidFromResponse(List<Imp> imps, Bid bid, List<BidderError> errors, String currency) {
        try {
            final BidType bidType = getBidType(bid.getImpid(), imps, bid.getId());
            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps, String bidId) {
        for (final Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(String.format("Response received for unexpected type of bid bidID: %s", bidId));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
