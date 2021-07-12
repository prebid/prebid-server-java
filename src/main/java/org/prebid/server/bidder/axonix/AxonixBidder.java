package org.prebid.server.bidder.axonix;

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
import org.prebid.server.proto.openrtb.ext.request.axonix.ExtImpAxonix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Axonix {@link Bidder} implementation.
 */
public class AxonixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAxonix>> AXONIX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAxonix>>() {
            };

    private static final String RESERVE_ENDPOINT_URL = "https://openrtb-us-east-1.axonix.com/supply/prebid-server/{{SupplyId}}";

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public AxonixBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = endpointUrl != null ? HttpUtil.validateUrl(endpointUrl) : null;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        final String supplyId;

        try {
            final ExtPrebid<?, ExtImpAxonix> extPrebid = mapper.mapper().convertValue(firstImp.getExt(),
                    AXONIX_EXT_TYPE_REFERENCE);
            final ExtImpAxonix extImpAxonix = extPrebid != null ? extPrebid.getBidder() : null;

            supplyId = extImpAxonix != null ? extImpAxonix.getSupplyId() : null;
            if (StringUtils.isEmpty(supplyId)) {
                return Result.withError(BidderError.badInput("Empty supplyId"));
            }
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(createRequest(request, supplyId));
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, String supplyId) {
        final String uri = StringUtils.isNotEmpty(endpointUrl) ? endpointUrl
                : HttpUtil.encodeUrl(RESERVE_ENDPOINT_URL.replace("{{SupplyId}}", supplyId));

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encode(request))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidRequest, bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
                return BidType.banner;
            }
        }
        return BidType.banner;
    }
}

