package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RequestUpdater {

    private final BlockedAttributes blockedAttributes;

    private RequestUpdater(BlockedAttributes blockedAttributes) {
        this.blockedAttributes = blockedAttributes;
    }

    public static RequestUpdater create(BlockedAttributes blockedAttributes) {
        return new RequestUpdater(Objects.requireNonNull(blockedAttributes));
    }

    public BidRequest update(BidRequest bidRequest) {
        final List<String> blockedAdomain = bidRequest.getBadv();
        final List<String> blockedAdvCat = bidRequest.getBcat();
        final Integer cattax = bidRequest.getCattax();
        final List<String> blockedApp = bidRequest.getBapp();

        return bidRequest.toBuilder()
                .badv(CollectionUtils.isNotEmpty(blockedAdomain) ? blockedAdomain : blockedAttributes.getBadv())
                .bcat(CollectionUtils.isNotEmpty(blockedAdvCat) ? blockedAdvCat : blockedAttributes.getBcat())
                .cattax(cattax != null ? cattax : blockedAttributes.getCattaxComplement())
                .bapp(CollectionUtils.isNotEmpty(blockedApp) ? blockedApp : blockedAttributes.getBapp())
                .imp(updateImps(bidRequest.getImp()))
                .build();
    }

    private List<Imp> updateImps(List<Imp> imps) {
        final Map<String, List<Integer>> blockedBannerType = blockedAttributes.getBtype();
        final Map<MediaType, Map<String, List<Integer>>> blockedAttr = blockedAttributes.getBattr();

        if (MapUtils.isEmpty(blockedBannerType) && MapUtils.isEmpty(blockedAttr)) {
            return imps;
        }

        return imps.stream()
                .map(imp -> updateImp(imp, blockedBannerType, blockedAttr))
                .toList();
    }

    private Imp updateImp(Imp imp,
                          Map<String, List<Integer>> blockedBannerType,
                          Map<MediaType, Map<String, List<Integer>>> blockedAttr) {

        final String impId = imp.getId();
        final List<Integer> btypeForImp = blockedBannerType != null ? blockedBannerType.get(impId) : null;
        final List<Integer> bannerBattrForImp = extractBattr(blockedAttr, MediaType.BANNER, impId);
        final List<Integer> videoBattrForImp = extractBattr(blockedAttr, MediaType.VIDEO, impId);
        final List<Integer> audioBattrForImp = extractBattr(blockedAttr, MediaType.AUDIO, impId);

        if (CollectionUtils.isEmpty(btypeForImp)
                && CollectionUtils.isEmpty(bannerBattrForImp)
                && CollectionUtils.isEmpty(videoBattrForImp)
                && CollectionUtils.isEmpty(audioBattrForImp)) {

            return imp;
        }

        final Banner banner = imp.getBanner();
        final Video video = imp.getVideo();
        final Audio audio = imp.getAudio();

        return imp.toBuilder()
                .banner(CollectionUtils.isNotEmpty(btypeForImp) || CollectionUtils.isNotEmpty(bannerBattrForImp)
                        ? updateBanner(banner, btypeForImp, bannerBattrForImp)
                        : banner)
                .video(CollectionUtils.isNotEmpty(videoBattrForImp)
                        ? updateVideo(imp.getVideo(), videoBattrForImp)
                        : video)
                .audio(CollectionUtils.isNotEmpty(audioBattrForImp)
                        ? updateAudio(imp.getAudio(), audioBattrForImp)
                        : audio)
                .build();
    }

    private static List<Integer> extractBattr(Map<MediaType, Map<String, List<Integer>>> blockedAttr,
                                              MediaType mediaType,
                                              String impId) {

        final Map<String, List<Integer>> impIdToBattr = blockedAttr != null ? blockedAttr.get(mediaType) : null;
        return impIdToBattr != null ? impIdToBattr.get(impId) : null;
    }

    private static Banner updateBanner(Banner banner, List<Integer> btype, List<Integer> battr) {
        final List<Integer> existingBtype = banner != null ? banner.getBtype() : null;
        final List<Integer> existingBattr = banner != null ? banner.getBattr() : null;

        return CollectionUtils.isEmpty(existingBtype) || CollectionUtils.isEmpty(existingBattr)
                ? Optional.ofNullable(banner)
                .map(Banner::toBuilder)
                .orElseGet(Banner::builder)
                .btype(CollectionUtils.isNotEmpty(existingBtype) ? existingBtype : btype)
                .battr(CollectionUtils.isNotEmpty(existingBattr) ? existingBattr : battr)
                .build()
                : banner;
    }

    private static Video updateVideo(Video video, List<Integer> battr) {
        final List<Integer> existingBattr = video != null ? video.getBattr() : null;
        return CollectionUtils.isEmpty(existingBattr)
                ? Optional.ofNullable(video)
                .map(Video::toBuilder)
                .orElseGet(Video::builder)
                .battr(battr)
                .build()
                : video;
    }

    private static Audio updateAudio(Audio audio, List<Integer> battr) {
        final List<Integer> existingBattr = audio != null ? audio.getBattr() : null;
        return CollectionUtils.isEmpty(existingBattr)
                ? Optional.ofNullable(audio)
                .map(Audio::toBuilder)
                .orElseGet(Audio::builder)
                .battr(battr)
                .build()
                : audio;
    }
}
