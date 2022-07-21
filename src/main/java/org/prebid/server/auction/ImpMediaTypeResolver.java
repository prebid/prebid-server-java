package org.prebid.server.auction;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Objects;

public class ImpMediaTypeResolver {

    private ImpMediaTypeResolver() {
    }

    public static ImpMediaType resolve(String bidImpId, List<Imp> imps, BidType bidType) {
        return switch (bidType) {
            case banner -> ImpMediaType.banner;
            case xNative -> ImpMediaType.xNative;
            case audio -> ImpMediaType.audio;
            case video -> resolveBidAdjustmentVideoMediaType(bidImpId, imps);
        };
    }

    private static ImpMediaType resolveBidAdjustmentVideoMediaType(String bidImpId, List<Imp> imps) {
        final Video bidImpVideo = imps.stream()
                .filter(imp -> imp.getId().equals(bidImpId))
                .map(Imp::getVideo)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (bidImpVideo == null) {
            return null;
        }

        final Integer placement = bidImpVideo.getPlacement();
        return placement == null || Objects.equals(placement, 1)
                ? ImpMediaType.video
                : ImpMediaType.video_outstream;
    }
}
