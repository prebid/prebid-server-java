package org.prebid.server.bidder.onetag;

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
import org.prebid.server.proto.openrtb.ext.request.onetag.ExtImpOnetag;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OnetagBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOnetag>> ONETAG_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String URL_PUBLISHER_ID_MACRO = "{{publisherId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OnetagBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        String requestPubId = null;
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpOnetag impExt = parseImpExt(imp);
                requestPubId = resolveAndValidatePubId(impExt.getPubId(), requestPubId);

                modifiedImps.add(imp.toBuilder().ext(impExt.getExt()).build());
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValue(createRequest(request, modifiedImps, requestPubId));
    }

    private ExtImpOnetag parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ONETAG_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String resolveAndValidatePubId(String impExtPubId, String requestPubId) {
        if (StringUtils.isEmpty(impExtPubId)) {
            throw new PreBidException("The publisher ID must not be empty");
        }
        if (requestPubId != null && !impExtPubId.equals(requestPubId)) {
            throw new PreBidException("There must be only one publisher ID");
        }
        return impExtPubId;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, List<Imp> imps, String pubId) {
        final String url = endpointUrl.replace(URL_PUBLISHER_ID_MACRO, pubId);
        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
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

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        throw new PreBidException(String.format("The impression with ID %s is not present into the request", impId));
    }
}
