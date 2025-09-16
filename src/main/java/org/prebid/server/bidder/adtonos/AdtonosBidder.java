package org.prebid.server.bidder.adtonos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.adtonos.ExtImpAdtonos;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdtonosBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdtonos>> ADTONOS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String PUBLISHER_ID_MACRO = "{{PublisherId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdtonosBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            final ExtImpAdtonos impExt = parseImpExt(bidRequest.getImp().getFirst());
            return Result.withValue(BidderUtil.defaultRequest(bidRequest, makeUrl(impExt), mapper));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private ExtImpAdtonos parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADTONOS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "Invalid imp.ext.bidder for impression index 0. Error Infomation: " + e.getMessage());
        }
    }

    private String makeUrl(ExtImpAdtonos extImp) {
        return endpointUrl.replace(PUBLISHER_ID_MACRO, extImp.getSupplierId());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(bidResponse, httpCall.getRequest().getPayload(), errors);

        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               BidRequest bidRequest,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), bidRequest, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, BidRequest bidRequest, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid, bidRequest.getImp()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid, List<Imp> imps) throws PreBidException {
        final Integer markupType = bid.getMtype();
        if (markupType != null) {
            switch (markupType) {
                case 1 -> {
                    return BidType.banner;
                }
                case 2 -> {
                    return BidType.video;
                }
                case 3 -> {
                    return BidType.audio;
                }
                case 4 -> {
                    return BidType.xNative;
                }
            }
        }

        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getAudio() != null) {
                    return BidType.audio;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException("Unsupported bidtype for bid: " + bid.getId());
            }
        }

        throw new PreBidException("Failed to find impression: " + impId);
    }
}
