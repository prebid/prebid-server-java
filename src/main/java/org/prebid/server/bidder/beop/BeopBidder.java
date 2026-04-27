package org.prebid.server.bidder.beop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.beop.ExtImpBeop;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class BeopBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBeop>> BEOP_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BeopBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            return Result.withError(BidderError.badInput("No impressions provided"));
        }

        final ExtImpBeop extImpBeop;
        try {
            extImpBeop = parseImpExt(imps.getFirst());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String resolvedUrl;
        try {
            resolvedUrl = buildEndpointUrl(extImpBeop);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(BidderUtil.defaultRequest(bidRequest, resolvedUrl, mapper));
    }

    private ExtImpBeop parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BEOP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided: " + e.getMessage());
        }
    }

    private String buildEndpointUrl(ExtImpBeop ext) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid endpoint URL: " + e.getMessage());
        }

        final String pid = StringUtils.trimToNull(ext.getBeopPublisherId());
        if (StringUtils.isNotEmpty(pid)) {
            uriBuilder.addParameter("pid", pid);
        }

        final String nid = StringUtils.trimToNull(ext.getBeopNetworkId());
        if (StringUtils.isNotEmpty(nid)) {
            uriBuilder.addParameter("nid", nid);
        }

        final String nptnid = StringUtils.trimToNull(ext.getBeopNetworkPartnerId());
        if (StringUtils.isNotEmpty(nptnid)) {
            uriBuilder.addParameter("nptnid", nptnid);
        }

        return uriBuilder.toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(bidResponse, errors);
        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
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

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid) {
        final Integer mtype = bid.getMtype();
        if (mtype == null) {
            throw new PreBidException(
                    "Failed to parse bid mtype for impression \"%s\"".formatted(bid.getImpid()));
        }
        return switch (mtype) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException(
                    "Failed to parse bid mtype for impression \"%s\"".formatted(bid.getImpid()));
        };
    }
}
