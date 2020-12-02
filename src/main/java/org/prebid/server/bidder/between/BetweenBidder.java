package org.prebid.server.bidder.between;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.request.between.ExtImpBetween;
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

/**
 * Between {@link Bidder} implementation.
 */
public class BetweenBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBetween>> BETWEEN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBetween>>() {
            };
    private static final String URL_HOST_MACRO = "{{Host}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BetweenBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<Imp, ExtImpBetween> validImpsWithExts = new HashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpBetween extImpBetween = parseImpExt(imp);
                validImpsWithExts.put(imp, extImpBetween);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImpsWithExts.size() == 0) {
            return Result.withError(BidderError.badInput("No valid Imps in Bid Request"));
        }

        final List<HttpRequest<BidRequest>> madeRequests = new ArrayList<>();

        for (Map.Entry<Imp, ExtImpBetween> entry : validImpsWithExts.entrySet()) {
            madeRequests.add(makeSingleRequest(entry.getValue(), request, entry.getKey()));
        }

        return Result.of(madeRequests, errors);
    }

    private ExtImpBetween parseImpExt(Imp imp) {
        final ExtImpBetween extImpBetween;
        try {
            extImpBetween = mapper.mapper().convertValue(imp.getExt(), BETWEEN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Missing bidder ext in impression with id: %s", imp.getId()));
        }
        if (StringUtils.isBlank(extImpBetween.getHost())) {
            throw new PreBidException(String.format("Invalid/Missing Host in impression with id: %s", imp.getId()));
        }
        return extImpBetween;
    }

    private HttpRequest<BidRequest> makeSingleRequest(ExtImpBetween extImpBetween, BidRequest request, Imp imp) {
        final String url = endpointUrl.replace(URL_HOST_MACRO, extImpBetween.getHost());
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
                        .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
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
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(String impId, List<Imp> imps) {
        // TODO add video/native, maybe audio banner types when demand appears

        return BidType.banner;
    }
}
