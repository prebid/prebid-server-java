package org.prebid.server.bidder.openweb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.openweb.ExtImpOpenweb;
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

public class OpenWebBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOpenweb>> OPENWEB_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public OpenWebBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Objects.requireNonNull(HttpUtil.validateUrl(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Map<Integer, List<Imp>> sourceIdToModifiedImp = new HashMap<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpOpenweb extImpOpenweb = parseImpExt(imp);
                final Integer sourceId = extImpOpenweb.getSourceId();
                final Imp modifiedImp = modifyImp(imp, extImpOpenweb);

                if (sourceIdToModifiedImp.containsKey(sourceId)) {
                    sourceIdToModifiedImp.get(sourceId).add(modifiedImp);
                } else {
                    sourceIdToModifiedImp.put(sourceId, new ArrayList<>(Collections.singletonList(modifiedImp)));
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (sourceIdToModifiedImp.isEmpty()) {
            return Result.withErrors(errors);
        }
        return Result.of(makeGroupRequests(request, sourceIdToModifiedImp), errors);
    }

    private ExtImpOpenweb parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OPENWEB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format(
                            "ignoring imp id=%s, error while encoding impExt, err: %s",
                            imp.getId(),
                            e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp, ExtImpOpenweb impExt) {
        final ObjectNode modifiedImpExt = mapper.mapper().createObjectNode()
                .set("openweb", mapper.mapper().valueToTree(impExt));
        final BigDecimal bidFloor = impExt.getBidFloor();
        final BigDecimal resolvedBidFloor = BidderUtil.isValidPrice(bidFloor)
                ? bidFloor
                : imp.getBidfloor();

        return imp.toBuilder()
                .bidfloor(resolvedBidFloor)
                .ext(modifiedImpExt)
                .build();
    }

    private List<HttpRequest<BidRequest>> makeGroupRequests(BidRequest request,
                                                            Map<Integer, List<Imp>> sourceIdToImps) {

        return sourceIdToImps.entrySet().stream()
                .map(impGroupEntry -> makeGroupRequest(request, impGroupEntry.getValue(), impGroupEntry.getKey()))
                .collect(Collectors.toList());
    }

    private HttpRequest<BidRequest> makeGroupRequest(BidRequest request, List<Imp> imps, Integer sourceId) {
        final BidRequest modifiedRequest = request.toBuilder().imp(imps).build();
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(sourceId))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(modifiedRequest))
                .payload(modifiedRequest)
                .build();
    }

    private String resolveEndpoint(Integer sourceId) {
        return String.format("%s?aid=%d", endpointUrl, sourceId);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bid, bidResponse, bidRequest.getImp(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid toBidderBid(Bid bid, BidResponse bidResponse, List<Imp> imps, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid.getId(), bid.getImpid(), imps), bidResponse.getCur());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(String bidId, String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getBanner() != null) {
                    return BidType.banner;
                }
            }
        }

        throw new PreBidException(
                String.format(
                        "ignoring bid id=%s, request doesn't contain any impression with id=%s",
                        bidId,
                        impId));
    }
}
