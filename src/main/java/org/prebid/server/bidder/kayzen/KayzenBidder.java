package org.prebid.server.bidder.kayzen;

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
import org.prebid.server.proto.openrtb.ext.request.kayzen.ExtImpKayzen;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Kayzen {@link Bidder} implementation.
 */
public class KayzenBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKayzen>> KAYZEN_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpKayzen>>() {
            };
    private static final String URL_ZONE_ID_MACRO = "{{ZoneID}}";
    private static final String URL_ACCOUNT_ID_MACRO = "{{AccountID}}";
    private static final int FIRST_IMP_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public KayzenBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> originalImps = request.getImp();
        final Imp firstImp = originalImps.get(FIRST_IMP_INDEX);
        final ExtImpKayzen extImpKayzen;

        try {
            extImpKayzen = parseImpExt(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> modifiedImps = new ArrayList<>(originalImps);
        modifiedImps.set(FIRST_IMP_INDEX, firstImp.toBuilder().ext(null).build());

        return Result.withValue(createRequest(extImpKayzen, request, modifiedImps));
    }

    private ExtImpKayzen parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), KAYZEN_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Missing bidder ext in impression with id: %s", imp.getId()));
        }
    }

    private HttpRequest<BidRequest> createRequest(ExtImpKayzen extImpKayzen, BidRequest request, List<Imp> imps) {
        final String url = endpointUrl.replace(URL_ZONE_ID_MACRO, extImpKayzen.getZone())
                .replace(URL_ACCOUNT_ID_MACRO, extImpKayzen.getExchange());
        final BidRequest outgoingRequest = request.toBuilder().imp(imps).build();

        return HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(url)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
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
                .map(bid -> BidderBid.of(bid,
                        getBidMediaType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
