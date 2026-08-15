package org.prebid.server.bidder.adplayx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adplayx.ExtImpAdplayx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdplayxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdplayx>> ADPLAYX_EXT_TYPE_REFERENCE =
            new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdplayxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdplayx extImp = parseImpExt(imp);

                if (StringUtils.isBlank(extImp.getApptoken())) {
                    errors.add(BidderError.badInput("apptoken is required"));
                    continue;
                }

                final String uri = buildEndpointUrl(extImp);

                // Clone bid request for this impression
                final BidRequest outgoingRequest = request.toBuilder()
                        .imp(Collections.singletonList(imp))
                        .build();

                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(uri)
                        .headers(HttpUtil.headers())
                        .body(mapper.encodeToBytes(outgoingRequest))
                        .payload(outgoingRequest)
                        .build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpAdplayx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADPLAYX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext: " + e.getMessage());
        }
    }

    private String buildEndpointUrl(ExtImpAdplayx extImp) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(endpointUrl);
            uriBuilder.addParameter("apptoken", extImp.getApptoken());

            if (StringUtils.isNotBlank(extImp.getPlacementid())) {
                uriBuilder.addParameter("placementid", extImp.getPlacementid());
            }

            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException(String.format("Invalid url: %s, error: %s", endpointUrl, e.getMessage()));
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final String responseBody = httpCall.getResponse().getBody();
        if (StringUtils.isBlank(responseBody)) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(responseBody, BidResponse.class);
            if (bidResponse == null || bidResponse.getSeatbid() == null) {
                return Result.empty();
            }

            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = new ArrayList<>();

            for (final SeatBid seatBid : bidResponse.getSeatbid()) {
                for (final Bid bid : seatBid.getBid()) {
                    try {
                        final BidType bidType = getBidType(bid.getImpid(), bidRequest.getImp());
                        bidderBids.add(BidderBid.of(bid, bidType, bidResponse.getCur()));
                    } catch (final PreBidException e) {
                        errors.add(BidderError.badServerResponse(e.getMessage()));
                    }
                }
            }

            return Result.of(bidderBids, errors);
        } catch (final Exception e) {
            return Result.withError(BidderError.badServerResponse("Failed to decode response: " + e.getMessage()));
        }
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getAudio() != null) {
                    return BidType.audio;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        throw new PreBidException("Failed to find impression with id: " + impId);
    }
}
