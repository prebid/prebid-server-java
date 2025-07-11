package org.prebid.server.auction.adpodding;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AdPoddingImpDowngradingService {

    private static final String IMP_ID_PATTERN = "%s-%s";

    private final BidderCatalog bidderCatalog;

    public AdPoddingImpDowngradingService(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public List<Imp> downgrade(Imp imp, String bidder, BidderAliases aliases) {
        final String resolvedBidder = aliases.resolveBidder(bidder);
        final BidderInfo bidderInfo = bidderCatalog.bidderInfoByName(resolvedBidder);
        final OrtbVersion ortbVersion = bidderInfo.getOrtbVersion();
        final boolean adpodSupported = bidderInfo.isAdpodSupported();
        final Audio audio = imp.getAudio();
        final Video video = imp.getVideo();

        if ((audio == null && video == null) || (ortbVersion == OrtbVersion.ORTB_2_6 && adpodSupported)) {
            return Collections.singletonList(imp);
        }

        final List<Imp> downgradedImps = new ArrayList<>();

        if (video != null && video.getPodid() != null) {
            if (video.getPoddur() == null) {
                downgradedImps.addAll(structuredImps(imp, video, downgradedImps.size()));
            } else {
                downgradedImps.addAll(dynamicImps(imp, video, downgradedImps.size()));
            }
        }

        if (audio != null && audio.getPodid() != null) {
            if (audio.getPoddur() == null) {
                downgradedImps.addAll(structuredImps(imp, audio, downgradedImps.size()));
            } else {
                downgradedImps.addAll(dynamicImps(imp, audio, downgradedImps.size()));
            }
        }

        //todo: TBD
        return downgradedImps.isEmpty() ? Collections.singletonList(imp) : downgradedImps;
    }

    private static List<Imp> structuredImps(Imp imp, Video video, int startIndex) {
        final Integer minDuration = video.getMinduration();
        final Integer maxDuration = video.getMaxduration();
        final List<Integer> rqddurs = video.getRqddurs();
        if ((minDuration != null && maxDuration != null) || CollectionUtils.isEmpty(rqddurs)) {
            return Collections.emptyList();
        }

        return buildStructuredImps(
                imp,
                startIndex,
                rqddurs,
                (builder, minDur, maxDur) -> builder
                        .audio(null)
                        .video(downgradeVideo(video, minDur, maxDur)));
    }

    private static List<Imp> structuredImps(Imp imp, Audio audio, int startIndex) {
        final Integer minDuration = audio.getMinduration();
        final Integer maxDuration = audio.getMaxduration();
        final List<Integer> rqddurs = audio.getRqddurs();
        if ((minDuration != null && maxDuration != null) || CollectionUtils.isEmpty(rqddurs)) {
            return Collections.emptyList();
        }

        return buildStructuredImps(
                imp,
                startIndex,
                rqddurs,
                (builder, minDur, maxDur) -> builder
                        .video(null)
                        .audio(downgradeAudio(audio, minDur, maxDur)));
    }

    private static List<Imp> buildStructuredImps(Imp imp,
                                                 int startIndex,
                                                 List<Integer> rqddurs,
                                                 MediaSpecificBuilder mediaBuilder) {

        final List<Imp> structuredImps = new ArrayList<>();
        for (int i = startIndex, j = 0; j < rqddurs.size(); i++, j++) {
            final Imp structuredImp = mediaBuilder
                    .apply(imp.toBuilder(), rqddurs.get(j), rqddurs.get(j))
                    .id(IMP_ID_PATTERN.formatted(imp.getId(), i))
                    .build();

            structuredImps.add(structuredImp);
        }

        return structuredImps;
    }

    private static Collection<Imp> dynamicImps(Imp imp, Video video, int startIndex) {
        final Integer minduration = video.getMinduration();
        final Integer maxduration = video.getMaxduration();

        final int impCount = calculateImpCountForPod(
                video.getMaxseq(),
                video.getRqddurs(),
                video.getPoddur(),
                video.getMinduration(),
                video.getMaxduration());

        if (impCount == 0) {
            return Collections.emptyList();
        }

        return buildDynamicImps(
                imp,
                minduration,
                maxduration,
                startIndex,
                impCount,
                (builder, minDur, maxDur) -> builder
                        .audio(null)
                        .video(downgradeVideo(video, minDur, maxDur)));
    }

    private static Collection<Imp> dynamicImps(Imp imp, Audio audio, int startIndex) {
        final Integer minduration = audio.getMinduration();
        final Integer maxduration = audio.getMaxduration();

        final int impCount = calculateImpCountForPod(
                audio.getMaxseq(),
                audio.getRqddurs(),
                audio.getPoddur(),
                minduration,
                maxduration);

        if (impCount == 0) {
            return Collections.emptyList();
        }

        return buildDynamicImps(
                imp,
                minduration,
                maxduration,
                startIndex,
                impCount,
                (builder, minDur, maxDur) -> builder
                        .video(null)
                        .audio(downgradeAudio(audio, minDur, maxDur)));
    }

    private static int calculateImpCountForPod(Integer maxseq,
                                               List<Integer> rqddurs,
                                               Integer poddur,
                                               Integer minduration,
                                               Integer maxduration) {

        if (maxseq != null && maxseq > 0) {
            return maxseq;
        } else if (CollectionUtils.isNotEmpty(rqddurs)) {
            final Integer minRequiredDuration = rqddurs.stream()
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            if (minRequiredDuration != null && minRequiredDuration > 0) {
                return poddur / minRequiredDuration;
            }
        } else if (minduration != null && minduration > 0) {
            return poddur / minduration;
        } else if (maxduration != null && maxduration > 0) {
            return poddur / maxduration;
        }

        return 0;
    }

    private static List<Imp> buildDynamicImps(Imp imp,
                                              Integer minduration,
                                              Integer maxduration,
                                              int startIndex,
                                              int count,
                                              MediaSpecificBuilder mediaBuilder) {

        final List<Imp> dynamicImps = new ArrayList<>();

        for (int i = startIndex, j = 0; j < count; i++, j++) {
            final Imp dynamicImp = mediaBuilder
                    .apply(imp.toBuilder(), minduration, maxduration)
                    .id(IMP_ID_PATTERN.formatted(imp.getId(), i))
                    .build();
            dynamicImps.add(dynamicImp);
        }

        return dynamicImps;
    }

    private static Video downgradeVideo(Video video, Integer minDuration, Integer maxDuration) {
        return video.toBuilder()
                .minduration(minDuration)
                .maxduration(maxDuration)
                .maxseq(null)
                .poddur(null)
                .podid(null)
                .podseq(null)
                .rqddurs(null)
                .slotinpod(null)
                .mincpmpersec(null)
                .poddedupe(null)
                .build();
    }

    private static Audio downgradeAudio(Audio audio, Integer minDuration, Integer maxDuration) {
        return audio.toBuilder()
                .minduration(minDuration)
                .maxduration(maxDuration)
                .maxseq(null)
                .poddur(null)
                .podid(null)
                .podseq(null)
                .rqddurs(null)
                .slotinpod(null)
                .mincpmpersec(null)
                .build();
    }

    @FunctionalInterface
    private interface MediaSpecificBuilder {
        Imp.ImpBuilder apply(Imp.ImpBuilder builder, Integer minDuration, Integer maxDuration);
    }

}
