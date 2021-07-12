package org.prebid.server.bidder.axonix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public AxonixBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final ExtImpAxonix extImpAxonix = mapper.mapper().convertValue(request.getImp().get(0).getExt(),
                    AXONIX_EXT_TYPE_REFERENCE).getBidder();

            return Result.withValue(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(HttpUtil.encodeUrl(endpointUrl.replace("{{SupplyId}}", extImpAxonix.getSupplyId())))
                    .headers(HttpUtil.headers())
                    .payload(request)
                    .body(mapper.encode(request))
                    .build());
        } catch (IllegalArgumentException | PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
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

