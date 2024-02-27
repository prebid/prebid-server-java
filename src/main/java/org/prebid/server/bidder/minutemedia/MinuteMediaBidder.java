package org.prebid.server.bidder.minutemedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.minutemedia.model.ExtImpMinuteMedia;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MinuteMediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMinuteMedia>> MINUTE_MEDIA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    public static final String PUBLISHER_ID_MACRO = "{{PublisherId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MinuteMediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final String orgId;

        try {
            orgId = extractFirstImpOrdId(bidRequest.getImp());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(BidderUtil.defaultRequest(bidRequest, resolveEndpoint(endpointUrl, orgId), mapper));
    }

    private String extractFirstImpOrdId(List<Imp> imps) {
        return imps.stream()
                .findFirst()
                .map(this::parseImpExt)
                .map(ExtImpMinuteMedia::getOrg)
                .map(String::strip)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new PreBidException(
                        "Failed to extract bidrequest.imp[0].ext.prebid.bidder.minutemedia.org parameter"));
    }

    private ExtImpMinuteMedia parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MINUTE_MEDIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private String resolveEndpoint(String endpointUrl, String orgId) {
        return endpointUrl.replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(orgId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
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
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        return BidType.banner;
    }
}
