package org.prebid.server.bidder.synacormedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.synacormedia.ExtImpSynacormedia;
import org.prebid.server.proto.openrtb.ext.request.synacormedia.ExtRequestSynacormedia;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Synacormedia {@link Bidder} implementation.
 */
public class SynacormediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSynacormedia>> SYNACORMEDIA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSynacormedia>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SynacormediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        ExtImpSynacormedia firstExtImp = null;

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpSynacormedia extImpSynacormedia;
            try {
                extImpSynacormedia = parseAndValidateExtImp(imp.getExt());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(String.format("Invalid Impression: %s", e.getMessage())));
                continue;
            }

            final Imp updatedImp = imp.toBuilder().tagid(extImpSynacormedia.getTagId()).build();
            validImps.add(updatedImp);

            if (firstExtImp == null) {
                firstExtImp = extImpSynacormedia;
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(validImps)
                .ext(mapper.fillExtension(ExtRequest.empty(), ExtRequestSynacormedia.of(firstExtImp.getSeatId())))
                .build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .headers(HttpUtil.headers())
                        .uri(endpointUrl.replaceAll("\\{\\{Host}}", firstExtImp.getSeatId()))
                        .body(mapper.encode(outgoingRequest))
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private ExtImpSynacormedia parseAndValidateExtImp(ObjectNode impExt) {
        final ExtImpSynacormedia extImp = parseExtImp(impExt);

        if (StringUtils.isBlank(extImp.getSeatId()) || StringUtils.isBlank(extImp.getTagId())) {
            throw new PreBidException("imp.ext has no seatId or tagId");
        }

        return extImp;
    }

    private ExtImpSynacormedia parseExtImp(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt, SYNACORMEDIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> mapBidToBidderBid(bid, bidRequest.getImp(), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid mapBidToBidderBid(Bid bid, List<Imp> imps, String currency) {
        final BidType bidType = getBidType(bid.getImpid(), imps);

        if (bidType == BidType.banner || bidType == BidType.video) {
            return BidderBid.of(bid, bidType, currency);
        }
        return null;
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        return BidType.banner;
    }
}
