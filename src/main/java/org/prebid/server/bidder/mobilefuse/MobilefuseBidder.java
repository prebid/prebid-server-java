package org.prebid.server.bidder.mobilefuse;

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
import org.prebid.server.proto.openrtb.ext.request.mobilefuse.ExtImpMobilefuse;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mobilefuse {@link Bidder} implementation.
 */
public class MobilefuseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMobilefuse>> MOBILEFUSE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpMobilefuse>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MobilefuseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        ExtImpMobilefuse firstExtImpMobilefuse = null;
        for (Imp imp : request.getImp()) {
            final ExtImpMobilefuse impExt = parseImpExt(imp);
            if (impExt != null) {
                firstExtImpMobilefuse = impExt;
                break;
            }
        }

        if (firstExtImpMobilefuse == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpMobilefuse value"));
        }

        Imp requestImp = null;
        for (Imp imp : request.getImp()) {
            final Imp modifiedImp = modifyImp(imp, firstExtImpMobilefuse);
            if (modifiedImp != null) {
                requestImp = modifiedImp;
                break;
            }
        }

        if (requestImp == null) {
            return Result.withError(BidderError.badInput("No valid imps"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(requestImp)).build();
        final String body = mapper.encode(outgoingRequest);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(firstExtImpMobilefuse))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .body(body)
                .build());
    }

    private ExtImpMobilefuse parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MOBILEFUSE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Imp modifyImp(Imp imp, ExtImpMobilefuse extImpMobilefuse) {

        if (imp.getBanner() != null || imp.getVideo() != null) {
            final Imp.ImpBuilder impBuilder = imp.toBuilder();
            if (imp.getBanner() != null && imp.getVideo() != null) {
                impBuilder.video(null);
            }

            return impBuilder
                    .tagid(Objects.toString(extImpMobilefuse.getPlacementId(), "0"))
                    .ext(null)
                    .build();
        }
        return null;
    }

    private String makeUrl(ExtImpMobilefuse extImpMobilefuse) {
        final String baseUrl = String.format("%s%s", endpointUrl,
                Objects.toString(extImpMobilefuse.getPublisherId(), "0"));
        return "ext".equals(extImpMobilefuse.getTagidSrc())
                ? String.format("%s%s", baseUrl, "&tagid_src=ext")
                : baseUrl;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
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
                .map(bid -> BidderBid.of(bid, getBidType(bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(List<Imp> imps) {
        return CollectionUtils.isNotEmpty(imps)
                && imps.get(0).getVideo() != null ? BidType.video : BidType.banner;
    }
}
