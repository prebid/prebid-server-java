package org.prebid.server.bidder.alkimi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.alkimi.ExtImpAlkimi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AlkimiBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final String TYPE_BANNER = "Banner";
    private static final String TYPE_VIDEO = "Video";

    private static final TypeReference<ExtPrebid<?, ExtImpAlkimi>> ALKIMI_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    public AlkimiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> updatedImps = request.getImp().stream()
                .map(imp -> updateImp(imp, parseImpExt(imp)))
                .toList();

        final BidRequest outgoingRequest = request.toBuilder().imp(updatedImps).build();
        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper));
    }

    private ExtImpAlkimi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALKIMI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private Imp updateImp(Imp imp, ExtImpAlkimi extImpAlkimi) {
        final Integer position = extImpAlkimi.getPos();
        final Banner updatedBanner = updateBanner(imp.getBanner(), position);
        final Video updatedVideo = updateVideo(imp.getVideo(), position);

        return imp.toBuilder()
                .bidfloor(extImpAlkimi.getBidFloor())
                .banner(updatedBanner)
                .video(updatedVideo)
                .ext(makeImpExt(imp, updatedBanner, updatedVideo, extImpAlkimi))
                .build();
    }

    private Banner updateBanner(Banner banner, Integer position) {
        if (banner == null || CollectionUtils.isEmpty(banner.getFormat())) {
            return banner;
        }

        final Format firstFormat = banner.getFormat().get(0);
        return banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .pos(position)
                .build();
    }

    private Video updateVideo(Video video, Integer position) {
        return video != null ? video.toBuilder().pos(position).build() : null;
    }

    private ObjectNode makeImpExt(Imp imp, Banner banner, Video video, ExtImpAlkimi extImpAlkimi) {
        final ExtImpAlkimi.ExtImpAlkimiBuilder extBuilder = extImpAlkimi.toBuilder();

        if (banner != null) {
            extBuilder.width(banner.getW());
            extBuilder.height(banner.getH());
            extBuilder.impMediaType(TYPE_BANNER);
        }

        if (video != null) {
            extBuilder.width(video.getW());
            extBuilder.height(video.getH());
            extBuilder.impMediaType(TYPE_VIDEO);
        }

        extBuilder.adUnitCode(imp.getId());

        return mapper.mapper().valueToTree(ExtPrebid.of(null, extBuilder.build()));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        BidType bidType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return bidType;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                } else if (imp.getAudio() != null) {
                    bidType = BidType.audio;
                }
            }
        }
        return bidType;
    }
}
