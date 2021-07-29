package org.prebid.server.bidder.kubient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kubient.ExtImpKubient;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * kubient {@link Bidder} implementation.
 */
public class KubientBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKubient>> KUBIENT_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpKubient>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public KubientBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        for (Imp imp : request.getImp()) {
            try {
                validateImpExt(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        String body;
        try {
            body = mapper.encode(request);
        } catch (EncodeException e) {
            return Result.withError(
                    BidderError.badInput(String.format("Failed to encode request body, error: %s", e.getMessage())));
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build()),
                Collections.emptyList());
    }

    private void validateImpExt(Imp imp) {
        final ExtImpKubient extImpKubient;
        try {
            extImpKubient = mapper.mapper().convertValue(imp.getExt(), KUBIENT_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        if (StringUtils.isBlank(extImpKubient.getZoneId())) {
            throw new PreBidException("zoneid is empty");
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bidRequest, bidResponse.getCur(), bid, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private BidderBid toBidderBid(BidRequest bidRequest, String currency, Bid bid, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return imp.getVideo() != null ? BidType.video : BidType.banner;
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
