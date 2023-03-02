package org.prebid.server.bidder.videoheroes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.videoheroes.ExtImpVideoHeroes;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VideoHeroesBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpVideoHeroes>> VIDEO_HEROES_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String URL_PUBLISHER_ID_MACRO = "{{PublisherID}}";
    private static final int FIRST_IMP_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VideoHeroesBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> requestImps = request.getImp();
        final Imp firstImp = requestImps.get(FIRST_IMP_INDEX);

        final ExtImpVideoHeroes impExt;
        try {
            impExt = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(createRequest(modifyFirstImp(requestImps), request, impExt));
    }

    private static List<Imp> modifyFirstImp(List<Imp> imp) {
        final List<Imp> modifiedImps = new ArrayList<>(imp);
        final Imp modifiedFirstImp = imp.get(FIRST_IMP_INDEX).toBuilder().ext(null).build();
        modifiedImps.set(FIRST_IMP_INDEX, modifiedFirstImp);

        return modifiedImps;
    }

    private ExtImpVideoHeroes parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), VIDEO_HEROES_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String resolveEndpoint(String publisherId) {
        return endpointUrl.replace(URL_PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(publisherId));
    }

    private HttpRequest<BidRequest> createRequest(List<Imp> imp,
                                                  BidRequest request,
                                                  ExtImpVideoHeroes extImpVideoHeroes) {
        final BidRequest outgoingRequest = request.toBuilder().imp(imp).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(extImpVideoHeroes.getPlacementId()))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
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
                break;
            }
        }

        return BidType.banner;
    }
}
