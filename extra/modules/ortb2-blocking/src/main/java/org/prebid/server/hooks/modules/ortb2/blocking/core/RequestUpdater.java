package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RequestUpdater {

    private final BlockedAttributes blockedAttributes;

    private RequestUpdater(BlockedAttributes blockedAttributes) {
        this.blockedAttributes = blockedAttributes;
    }

    public static RequestUpdater create(BlockedAttributes blockedAttributes) {
        return new RequestUpdater(Objects.requireNonNull(blockedAttributes));
    }

    public BidRequest update(BidRequest bidRequest) {
        final List<String> blockedAdomain = blockedAttributes.getBadv();
        final List<String> blockedAdvCat = blockedAttributes.getBcat();
        final List<String> blockedApp = blockedAttributes.getBapp();

        return bidRequest.toBuilder()
            .badv(CollectionUtils.isNotEmpty(blockedAdomain) ? blockedAdomain : bidRequest.getBadv())
            .bcat(CollectionUtils.isNotEmpty(blockedAdvCat) ? blockedAdvCat : bidRequest.getBcat())
            .bapp(CollectionUtils.isNotEmpty(blockedApp) ? blockedApp : bidRequest.getBapp())
            .imp(updateImps(bidRequest.getImp()))
            .build();
    }

    private List<Imp> updateImps(List<Imp> imps) {
        final Map<String, List<Integer>> blockedBannerType = blockedAttributes.getBtype();
        final Map<String, List<Integer>> blockedBannerAttr = blockedAttributes.getBattr();

        if (MapUtils.isEmpty(blockedBannerType) && MapUtils.isEmpty(blockedBannerAttr)) {
            return imps;
        }

        return imps.stream()
            .map(imp -> updateImp(imp, blockedBannerType, blockedBannerAttr))
            .collect(Collectors.toList());
    }

    private Imp updateImp(
        Imp imp,
        Map<String, List<Integer>> blockedBannerType,
        Map<String, List<Integer>> blockedBannerAttr) {

        final String impId = imp.getId();
        final List<Integer> btypeForImp = blockedBannerType != null ? blockedBannerType.get(impId) : null;
        final List<Integer> battrForImp = blockedBannerAttr != null ? blockedBannerAttr.get(impId) : null;

        if (CollectionUtils.isEmpty(btypeForImp) && CollectionUtils.isEmpty(battrForImp)) {
            return imp;
        }

        final Banner banner = imp.getBanner();
        final List<Integer> existingBtype = banner != null ? banner.getBtype() : null;
        final List<Integer> existingBattr = banner != null ? banner.getBattr() : null;
        final Banner.BannerBuilder bannerBuilder = banner != null ? banner.toBuilder() : Banner.builder();

        return imp.toBuilder()
            .banner(bannerBuilder
                .btype(CollectionUtils.isNotEmpty(btypeForImp) ? btypeForImp : existingBtype)
                .battr(CollectionUtils.isNotEmpty(battrForImp) ? battrForImp : existingBattr)
                .build())
            .build();
    }
}
