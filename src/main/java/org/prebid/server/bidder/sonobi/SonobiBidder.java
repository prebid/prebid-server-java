package org.prebid.server.bidder.sonobi;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.prebid.server.proto.openrtb.ext.request.sonobi.ExtImpSonobi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SonobiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSonobi>> SONOBI_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SonobiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpSonobi extImpSonobi = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpSonobi.getTagId());
                requests.add(makeRequest(bidRequest, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpSonobi parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SONOBI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp, String tagId) {
        return imp.toBuilder().tagid(tagId).build();
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, Imp imp) {
        final BidRequest modifiedBidRequest = bidRequest.toBuilder().imp(Collections.singletonList(imp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(modifiedBidRequest))
                .payload(modifiedBidRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);

        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest,
                                               BidResponse bidResponse,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid, imps), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid, List<Imp> imps) throws PreBidException {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                return imp.getBanner() != null ? BidType.banner
                        : imp.getVideo() != null ? BidType.video
                        : BidType.banner;
            }
        }

        throw new PreBidException(String.format("Failed to find impression for ID: %s", impId));
    }
}
