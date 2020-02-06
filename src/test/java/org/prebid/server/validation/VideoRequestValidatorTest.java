package org.prebid.server.validation;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import com.iab.openrtb.request.video.VideoVideo;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class VideoRequestValidatorTest {

    private VideoRequestValidator videoRequestValidator;

    @Before
    public void setUp() {
        videoRequestValidator = new VideoRequestValidator();
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenStoredRequestEnforcedAndStoredRequestIdIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(identity(), identity()).toBuilder()
                .storedrequestid(null)
                .build();
        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, true, null))
                .withMessage("request missing required field: storedrequestid");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenPodConfigIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(identity(), identity()).toBuilder()
                .podconfig(null)
                .build();
        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, true, null))
                .withMessage("request missing required field: PodConfig");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenDurationRangeSecIsNegative() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                identity(),
                podconfigBuilder -> podconfigBuilder.durationRangeSec(singletonList(-100)));

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, true, null))
                .withMessage("duration array require only positive numbers");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenPodsIsEmpty() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                identity(),
                podconfigBuilder -> podconfigBuilder.pods(emptyList()));

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request missing required field: PodConfig.Pods");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenSiteAndAppIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.site(null).app(null),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request missing required field: site or app");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenSiteAndAppIsNotNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.site(Site.builder().build()).app(App.builder().build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request.site or request.app must be defined, but not both");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenSiteHaveNoId() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.site(Site.builder().build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request.site missing required field: id or page");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenAppHaveBlacklistedAccount() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.site(null).app(App.builder().id("BAD").build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, singletonList("BAD")))
                .withMessage("Prebid-server does not process requests from App ID: BAD");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenAppIdAndBundleIsEmpty() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.site(null).app(App.builder().id(null).bundle(null).build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, singletonList("BAD")))
                .withMessage("request.app missing required field: id or bundle");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenVideoIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.video(null),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request missing required field: Video");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenVideoMimesIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.video(VideoVideo.builder().build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request missing required field: Video.Mimes");
    }

    @Test
    public void validateStoredBidRequestShouldThrowExceptionWhenVideoProtocolsIsNull() {
        // given
        final BidRequestVideo requestVideo = givenBidRequestVideo(
                requestBuilder -> requestBuilder.video(VideoVideo.builder().mimes(singletonList("mime")).build()),
                identity());

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> videoRequestValidator.validateStoredBidRequest(requestVideo, false, null))
                .withMessage("request missing required field: Video.Protocols");
    }

    @Test
    public void validPodsShouldReturnExpectedResult() {
        // given
        final Pod pod = Pod.of(1, 20, "cnf1");
        final Pod duplicatedPod = Pod.of(1, 20, "cnf2");
        final Pod negativeIdPod = Pod.of(-100, 20, "cnf3");
        final Pod missingDurationPod = Pod.of(-100, null, "cnf4");
        final Pod negativeDurationPod = Pod.of(5, -10, "cnf5");
        final Pod missingConfigIdPod = Pod.of(6, -10, null);
        final Pod notStoredPod = Pod.of(7, 1, "conf19");
        final Pod normalPod = Pod.of(10, 1, "conf10");

        final Podconfig podConfig = Podconfig.builder().pods(Arrays.asList(pod, duplicatedPod, negativeDurationPod, missingConfigIdPod,
                negativeIdPod, notStoredPod, normalPod, missingDurationPod)).build();

        final Set<String> storedIds = new HashSet<>(Arrays.asList("cnf1", "cnf2", "cnf3", "cnf4", "cnf5", "conf10"));

        // when
        final WithPodErrors<List<Pod>> result = videoRequestValidator.validPods(podConfig, storedIds);

        // then
        assertThat(result.getData()).containsOnly(normalPod);
        assertThat(result.getPodErrors()).containsOnly(
                PodError.of(1, 1, singletonList("request duplicated required field: PodConfig.Pods.PodId, Pod id: 1")),
                PodError.of(5, 2, singletonList("request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative, Pod index: 2")),
                PodError.of(6, 3, Arrays.asList(
                        "request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative, Pod index: 3",
                        "request missing or incorrect required field: PodConfig.Pods.ConfigId, Pod index: 3")),
                PodError.of(-100, 4, singletonList("request missing required field: PodConfig.Pods.PodId, Pod index: 4")),
                PodError.of(-100, 7, Arrays.asList(
                        "request duplicated required field: PodConfig.Pods.PodId, Pod id: -100",
                        "request missing required field: PodConfig.Pods.PodId, Pod index: 7",
                        "request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec, Pod index: 7")),
                PodError.of(7, 5, singletonList("unable to load Pod id: 7")));
    }

    private BidRequestVideo givenBidRequestVideo(
            UnaryOperator<BidRequestVideo.BidRequestVideoBuilder> requestCustomizer,
            UnaryOperator<Podconfig.PodconfigBuilder> podconfigCustomizer) {

        return requestCustomizer.apply(BidRequestVideo.builder()
                .storedrequestid("storedrequestid")
                .podconfig(podconfigCustomizer.apply(Podconfig.builder()
                        .pods(singletonList(Pod.of(123, 100, "1")))
                        .durationRangeSec(Arrays.asList(200, 100)))
                        .build())
                .site(Site.builder().id("siteId").build())
                .video(VideoVideo.builder().mimes(singletonList("mime")).protocols(singletonList(123)).build()))
                .build();
    }

}
