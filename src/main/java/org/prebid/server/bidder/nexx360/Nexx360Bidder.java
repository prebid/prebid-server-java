package org.prebid.server.bidder.nexx360;

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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.nexx360.ExtImpNexx360;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Nexx360Bidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNexx360>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String BIDDER_NAME = "nexx360";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final PrebidVersionProvider prebidVersionProvider;

    public Nexx360Bidder(String endpointUrl, JacksonMapper mapper, PrebidVersionProvider prebidVersionProvider) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();

        String tagId = null;
        String placement = null;

        try {
            final List<Imp> imps = request.getImp();
            for (int i = 0; i < imps.size(); i++) {
                final Imp imp = imps.get(i);
                final ExtImpNexx360 extImp = parseBidderExt(imp);
                final Imp modifiedImp = imp.toBuilder()
                        .ext(mapper.mapper().createObjectNode().set(BIDDER_NAME, mapper.mapper().valueToTree(extImp)))
                        .build();
                modifiedImps.add(modifiedImp);

                if (i == 0) {
                    tagId = extImp.getTagId();
                    placement = extImp.getPlacement();
                }
            }
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest modifiedRequest = makeRequest(request, modifiedImps);
        final String url = makeUrl(tagId, placement);
        return Result.withValue(BidderUtil.defaultRequest(modifiedRequest, url, mapper));
    }

    private ExtImpNexx360 parseBidderExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest makeRequest(BidRequest request, List<Imp> imps) {
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty(BIDDER_NAME, mapper.mapper().valueToTree(
                Nexx360ExtRequest.of(Nexx360ExtRequestCaller.of(prebidVersionProvider.getNameVersionRecord()))));

        return request.toBuilder()
                .imp(imps)
                .ext(extRequest)
                .build();
    }

    private String makeUrl(String tagId, String placement) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid url: %s, error: %s".formatted(endpointUrl, e.getMessage()));
        }

        if (StringUtils.isNotBlank(placement)) {
            uriBuilder.addParameter("placement", placement);
        }
        if (StringUtils.isNotBlank(tagId)) {
            uriBuilder.addParameter("tag_id", tagId);
        }

        return uriBuilder.toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(Bid bid) {
        final String bidType;
        try {
            bidType = mapper.mapper()
                    .convertValue(bid.getExt(), Nexx360ExtBid.class)
                    .getBidType();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "unable to fetch mediaType in multi-format: " + bid.getImpid());
        }

        return switch (bidType) {
            case "banner" -> BidType.banner;
            case "video" -> BidType.video;
            case "audio" -> BidType.audio;
            case "native" -> BidType.xNative;
            default -> throw new PreBidException(
                    "unable to fetch mediaType in multi-format: " + bid.getImpid());
        };
    }
}
