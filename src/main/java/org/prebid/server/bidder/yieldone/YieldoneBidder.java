package org.prebid.server.bidder.yieldone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
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
import org.prebid.server.proto.openrtb.ext.request.yieldone.ExtImpYieldone;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Yieldone {@link Bidder} implementation.
 */
public class YieldoneBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpYieldone>> YIELDONE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpYieldone>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YieldoneBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validateImpExt(imp);
                final Imp updatedImp = modifyImp(imp);

                validImps.add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .body(mapper.encode(outgoingRequest))
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private Imp modifyImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner != null) {
            if (banner.getH() == null && banner.getW() == null && CollectionUtils.isNotEmpty(banner.getFormat())) {
                final Format firstFormat = banner.getFormat().get(0);
                final Banner modifiedBanner = banner.toBuilder()
                        .h(firstFormat.getH())
                        .w(firstFormat.getW())
                        .build();
                return imp.toBuilder().banner(modifiedBanner).build();
            }
        }
        return imp;
    }

    private void validateImpExt(Imp imp) {
        try {
            mapper.mapper().convertValue(imp.getExt(), YIELDONE_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = decodeBodyToBidResponse(httpCall);
            if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
                return Result.empty();
            }

            final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                    .filter(Objects::nonNull)
                    .map(SeatBid::getBid)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()),
                            bidResponse.getCur()))
                    .collect(Collectors.toList());

            return Result.withValues(bidderBids);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
            }
        }
        throw new PreBidException(String.format("Unknown impression type with id %s", impId));
    }
}
