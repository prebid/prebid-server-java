package org.prebid.server.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseVideoTargeting;
import org.prebid.server.proto.response.ExtAmpVideoPrebid;
import org.prebid.server.proto.response.ExtAmpVideoResponse;
import org.prebid.server.proto.response.VideoResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class VideoResponseFactory {

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };

    private final JacksonMapper mapper;

    public VideoResponseFactory(JacksonMapper mapper) {
        this.mapper = mapper;
    }

    public VideoResponse toVideoResponse(
            AuctionContext auctionContext,
            BidResponse bidResponse,
            List<PodError> podErrors) {

        final List<Bid> bids = bidsFrom(bidResponse);
        final boolean anyBidsReturned = CollectionUtils.isNotEmpty(bids);
        final List<ExtAdPod> adPods = adPodsWithTargetingFrom(bids);

        if (anyBidsReturned && CollectionUtils.isEmpty(adPods)) {
            throw new PreBidException("caching failed for all bids");
        }

        adPods.addAll(adPodsWithErrors(podErrors));

        final ExtResponseDebug extResponseDebug;
        final Map<String, List<ExtBidderError>> errors;
        // Fetch debug and errors information from response if requested
        if (auctionContext.getDebugContext().isDebugEnabled()) {
            final ExtBidResponse extBidResponse = bidResponse.getExt();

            extResponseDebug = extBidResponse != null ? extBidResponse.getDebug() : null;
            errors = extBidResponse != null ? extBidResponse.getErrors() : null;
        } else {
            extResponseDebug = null;
            errors = null;
        }
        return VideoResponse.of(adPods, extResponseDebug, errors, extResponseFrom(bidResponse));
    }

    private static List<Bid> bidsFrom(BidResponse bidResponse) {
        if (bidResponse != null && CollectionUtils.isNotEmpty(bidResponse.getSeatbid())) {
            return bidResponse.getSeatbid().stream()
                    .filter(Objects::nonNull)
                    .map(SeatBid::getBid)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private List<ExtAdPod> adPodsWithTargetingFrom(List<Bid> bids) {
        final List<ExtAdPod> adPods = new ArrayList<>();
        for (Bid bid : bids) {
            final Map<String, String> targeting = targeting(bid);
            if (findByPrefix(targeting, "hb_uuid") == null) {
                continue;
            }
            final String impId = bid.getImpid();
            final String podIdString = impId.split("_")[0];
            if (!NumberUtils.isDigits(podIdString)) {
                continue;
            }
            final Integer podId = Integer.parseInt(podIdString);

            final ExtResponseVideoTargeting videoTargeting = ExtResponseVideoTargeting.of(
                    findByPrefix(targeting, "hb_pb"),
                    findByPrefix(targeting, "hb_pb_cat_dur"),
                    findByPrefix(targeting, "hb_uuid"));

            ExtAdPod adPod = adPods.stream()
                    .filter(extAdPod -> extAdPod.getPodid().equals(podId))
                    .findFirst()
                    .orElse(null);

            if (adPod == null) {
                adPod = ExtAdPod.of(podId, new ArrayList<>(), null);
                adPods.add(adPod);
            }
            adPod.getTargeting().add(videoTargeting);
        }
        return adPods;
    }

    private Map<String, String> targeting(Bid bid) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extBid;
        try {
            extBid = mapper.mapper().convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            return Collections.emptyMap();
        }

        final ExtBidPrebid extBidPrebid = extBid != null ? extBid.getPrebid() : null;
        final Map<String, String> targeting = extBidPrebid != null ? extBidPrebid.getTargeting() : null;
        return targeting != null ? targeting : Collections.emptyMap();
    }

    private static String findByPrefix(Map<String, String> keyToValue, String prefix) {
        return keyToValue.entrySet().stream()
                .filter(keyAndValue -> keyAndValue.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static List<ExtAdPod> adPodsWithErrors(List<PodError> podErrors) {
        return podErrors.stream()
                .map(podError -> ExtAdPod.of(podError.getPodId(), null, podError.getPodErrors()))
                .collect(Collectors.toList());
    }

    private static ExtAmpVideoResponse extResponseFrom(BidResponse bidResponse) {
        final ExtBidResponse ext = bidResponse.getExt();
        final ExtBidResponsePrebid extPrebid = ext != null ? ext.getPrebid() : null;
        final ExtModules extModules = extPrebid != null ? extPrebid.getModules() : null;

        return extModules != null ? ExtAmpVideoResponse.of(ExtAmpVideoPrebid.of(extModules)) : null;
    }
}
