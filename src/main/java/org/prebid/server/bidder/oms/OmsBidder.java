package org.prebid.server.bidder.oms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.omx.ExtImpOms;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class OmsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOms>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OmsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (!request.getImp().isEmpty()) {
            try {
                final ExtImpOms impExt = parseImpExt(request.getImp().getFirst());
                final String publisherId = impExt.getPid() == null
                        && impExt.getPublisherId() != null
                        && impExt.getPublisherId() > 0
                        ? String.valueOf(impExt.getPublisherId())
                        : impExt.getPid();
                final String url = "%s?publisherId=%s".formatted(endpointUrl, publisherId);
                return Result.withValue(BidderUtil.defaultRequest(request, url, mapper));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValue(BidderUtil.defaultRequest(request, endpointUrl, mapper));
    }

    private ExtImpOms parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext. Imp.Id: " + imp.getId());
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> {
                    final BidType bidType = getBidType(bid);
                    return BidderBid.builder()
                            .bid(bid)
                            .type(bidType)
                            .bidCurrency(bidResponse.getCur())
                            .videoInfo(videoInfo(bidType, bid))
                            .build();
                })
                .toList();
    }

    private static BidType getBidType(Bid bid) {
        return Objects.equals(bid.getMtype(), 2) ? BidType.video : BidType.banner;
    }

    private static ExtBidPrebidVideo videoInfo(BidType bidType, Bid bid) {
        if (bidType != BidType.video) {
            return null;
        }
        final List<String> cat = bid.getCat();
        final Integer duration = bid.getDur();

        return ExtBidPrebidVideo.of(
                duration != null ? duration : 0,
                CollectionUtils.isNotEmpty(cat) ? cat.getFirst() : StringUtils.EMPTY
        );
    }
}
