package org.prebid.server.bidder.adtarget;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adtarget.proto.AdtargetImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdtargetBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdtarget>> ADTARGET_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdtarget>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdtargetBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Result<Map<Integer, List<Imp>>> sourceIdToImpsResult = mapSourceIdToImp(request.getImp());

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Map.Entry<Integer, List<Imp>> sourceIdToImps : sourceIdToImpsResult.getValue().entrySet()) {
            final String url = String.format("%s?aid=%d", endpointUrl,
                    ObjectUtils.defaultIfNull(sourceIdToImps.getKey(), 0));
            final BidRequest bidRequest = request.toBuilder().imp(sourceIdToImps.getValue()).build();
            httpRequests.add(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(url)
                    .body(mapper.encodeToBytes(bidRequest))
                    .headers(HttpUtil.headers())
                    .payload(bidRequest)
                    .build());
        }
        return Result.of(httpRequests, sourceIdToImpsResult.getErrors());
    }

    private Result<Map<Integer, List<Imp>>> mapSourceIdToImp(List<Imp> imps) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<Integer, List<Imp>> sourceToImps = new HashMap<>();
        for (Imp imp : imps) {
            final ExtImpAdtarget extImpAdtarget;
            try {
                validateImpression(imp);
                extImpAdtarget = parseImpAdtarget(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            final Imp updatedImp = updateImp(imp, extImpAdtarget);

            final Integer sourceId = extImpAdtarget.getSourceId();
            sourceToImps.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(updatedImp);
        }
        return Result.of(sourceToImps, errors);
    }

    private ExtImpAdtarget parseImpAdtarget(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADTARGET_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding impExt, err: %s", imp.getId(), e.getMessage()));
        }
    }

    private static void validateImpression(Imp imp) {
        final String impId = imp.getId();
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, Adtarget supports only Video and Banner", impId));
        }

        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException(String.format("ignoring imp id=%s, extImpBidder is empty", impId));
        }
    }

    private Imp updateImp(Imp imp, ExtImpAdtarget extImpAdtarget) {
        final AdtargetImpExt adtargetImpExt = AdtargetImpExt.of(extImpAdtarget);
        final BigDecimal bidFloor = extImpAdtarget.getBidFloor();
        return imp.toBuilder()
                .bidfloor(BidderUtil.isValidPrice(bidFloor) ? bidFloor : imp.getBidfloor())
                .ext(mapper.mapper().valueToTree(adtargetImpExt))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, bidRequest.getImp(), errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<Imp> imps, List<BidderError> errors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : createBiddersBid(bidResponse, imps, errors);
    }

    private static List<BidderBid> createBiddersBid(BidResponse bidResponse, List<Imp> imps, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(bid, imps, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid createBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), bid.getId(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(String bidImpId, String bidId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bidImpId)) {
                return imp.getVideo() != null ? BidType.video : BidType.banner;
            }
        }
        throw new PreBidException(String.format(
                "ignoring bid id=%s, request doesn't contain any impression with id=%s", bidId, bidImpId));
    }
}
