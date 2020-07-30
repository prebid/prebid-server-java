package org.prebid.server.validation;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VideoRequestValidator {

    /**
     * Throws {@link InvalidRequestException} in case of invalid {@link BidRequestVideo}.
     */
    public void validateStoredBidRequest(BidRequestVideo bidRequestVideo, boolean enforceStoredRequest,
                                         List<String> blacklistedAccounts) {
        if (enforceStoredRequest && StringUtils.isBlank(bidRequestVideo.getStoredrequestid())) {
            throw new InvalidRequestException("request missing required field: storedrequestid");
        }
        final Podconfig podconfig = bidRequestVideo.getPodconfig();
        if (podconfig == null) {
            throw new InvalidRequestException("request missing required field: PodConfig");
        } else {
            final List<Integer> durationRangeSec = podconfig.getDurationRangeSec();
            if (CollectionUtils.isEmpty(durationRangeSec) || isZeroOrNegativeDuration(durationRangeSec)) {
                throw new InvalidRequestException("duration array require only positive numbers");
            }
            final List<Pod> pods = podconfig.getPods();
            if (CollectionUtils.sizeIsEmpty(pods)) {
                throw new InvalidRequestException("request missing required field: PodConfig.Pods");
            }
        }

        validateSiteAndApp(bidRequestVideo.getSite(), bidRequestVideo.getApp(), blacklistedAccounts);
        validateVideo(bidRequestVideo.getVideo());
    }

    private static boolean isZeroOrNegativeDuration(List<Integer> durationRangeSec) {
        return durationRangeSec.stream()
                .anyMatch(duration -> duration <= 0);
    }

    private void validateSiteAndApp(Site site, App app, List<String> blacklistedAccounts) {
        if (app == null && site == null) {
            throw new InvalidRequestException("request missing required field: site or app");
        } else if (app != null && site != null) {
            throw new InvalidRequestException("request.site or request.app must be defined, but not both");
        } else if (site != null && StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
            throw new InvalidRequestException("request.site missing required field: id or page");
        } else if (app != null) {
            final String appId = app.getId();

            if (StringUtils.isNotBlank(appId)) {
                if (blacklistedAccounts.contains(appId)) {
                    throw new InvalidRequestException("Prebid-server does not process requests from App ID: "
                            + appId);
                }
            } else {
                if (StringUtils.isBlank(app.getBundle())) {
                    throw new InvalidRequestException("request.app missing required field: id or bundle");
                }
            }
        }
    }

    private static void validateVideo(Video video) {
        if (video == null) {
            throw new InvalidRequestException("request missing required field: Video");
        }
        final List<String> mimes = video.getMimes();
        if (CollectionUtils.isEmpty(mimes)) {
            throw new InvalidRequestException("request missing required field: Video.Mimes");
        } else {
            final List<String> notBlankMimes = mimes.stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(notBlankMimes)) {
                throw new InvalidRequestException(
                        "request missing required field: Video.Mimes, mime types contains empty strings only");
            }
        }

        if (CollectionUtils.isEmpty(video.getProtocols())) {
            throw new InvalidRequestException("request missing required field: Video.Protocols");
        }
    }

    public WithPodErrors<List<Pod>> validPods(Podconfig podconfig, Set<String> storedPodConfigIds) {
        final List<Pod> pods = podconfig.getPods();
        final List<PodError> podErrors = validateEachPod(pods);
        final List<Integer> errorPodIds = podErrors.stream()
                .map(PodError::getPodId)
                .collect(Collectors.toList());

        final List<Pod> validPods = new ArrayList<>();
        for (int i = 0; i < pods.size(); i++) {
            final Pod pod = pods.get(i);
            final Integer podId = pod.getPodId();
            final String configId = pod.getConfigId();
            if (!errorPodIds.contains(podId)) {
                if (storedPodConfigIds.contains(configId)) {
                    validPods.add(pod);
                } else {
                    podErrors.add(PodError.of(podId, i, Collections.singletonList("unable to load Pod id: " + podId)));
                }
            }
        }

        return WithPodErrors.of(validPods, podErrors);
    }

    private static List<PodError> validateEachPod(List<Pod> pods) {
        final List<PodError> podErrorsResult = new ArrayList<>();

        final Map<Integer, Boolean> podIdToFlag = new HashMap<>();
        for (int i = 0; i < pods.size(); i++) {
            final List<String> podErrors = new ArrayList<>();
            final Pod pod = pods.get(i);

            final Integer podId = pod.getPodId();
            if (BooleanUtils.isTrue(podIdToFlag.get(podId))) {
                podErrors.add("request duplicated required field: PodConfig.Pods.PodId, Pod id: " + podId);
            } else {
                podIdToFlag.put(podId, true);
            }
            if (podId == null || podId <= 0) {
                podErrors.add("request missing required field: PodConfig.Pods.PodId, Pod index: " + i);
            }
            final Integer adpodDurationSec = pod.getAdpodDurationSec();
            if (adpodDurationSec != null) {
                if (adpodDurationSec == 0) {
                    podErrors.add(
                            "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                    + i);
                }
                if (adpodDurationSec < 0) {
                    podErrors.add(
                            "request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative, Pod index: "
                                    + i);
                }
            } else {
                podErrors.add(
                        "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: "
                                + i);
            }
            if (StringUtils.isBlank(pod.getConfigId())) {
                podErrors.add("request missing or incorrect required field: PodConfig.Pods.ConfigId, Pod index: " + i);
            }
            if (!podErrors.isEmpty()) {
                podErrorsResult.add(PodError.of(podId, i, podErrors));
            }
        }
        return podErrorsResult;
    }

}
