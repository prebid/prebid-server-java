package org.prebid.server.auction.adpodding;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AdPoddingImpDowngradingServiceTest extends VertxTest {

    private static final String BIDDER_NAME = "test_bidder";
    private static final String IMP_ID = "imp_id";

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;
    @Mock
    private BidderInfo bidderInfo;

    private AdPoddingImpDowngradingService target;

    @BeforeEach
    public void setUp() {
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(bidderInfo);
        given(bidderAliases.resolveBidder(anyString())).willReturn(BIDDER_NAME);

        given(bidderInfo.getOrtbVersion()).willReturn(OrtbVersion.ORTB_2_5);
        given(bidderInfo.isAdpodSupported()).willReturn(false);

        target = new AdPoddingImpDowngradingService(bidderCatalog);
    }

    @Test
    public void downgradeShouldReturnOriginalImpWhenBidderSupportsAdPods() {
        // given
        given(bidderInfo.getOrtbVersion()).willReturn(OrtbVersion.ORTB_2_6);
        given(bidderInfo.isAdpodSupported()).willReturn(true);

        final Imp imp = givenImp(givenVideo(identity()));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(1).containsExactly(imp);
        verify(bidderCatalog).bidderInfoByName(BIDDER_NAME);
    }

    @Test
    public void downgradeShouldReturnOriginalImpWhenImpHasNoMedia() {
        // given
        final Imp imp = Imp.builder().id(IMP_ID).build();

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(1).containsExactly(imp);
    }

    @Test
    public void downgradeShouldReturnOriginalImpWhenMediaHasNoPodId() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video.podid(null)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(1).containsExactly(imp);
    }

    @Test
    public void downgradeShouldReturnEmptyListForStructuredVideoWhenRqddursIsEmpty() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video.poddur(null).rqddurs(emptyList())));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).containsExactly(imp);
    }

    @Test
    public void downgradeShouldReturnDowngradedImpsForStructuredVideo() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video.poddur(null).rqddurs(asList(15, 30, 45))));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(3)
                .extracting(Imp::getId, Imp::getVideo)
                .containsExactly(
                        tuple("imp_id-0", Video.builder().minduration(15).maxduration(15).build()),
                        tuple("imp_id-1", Video.builder().minduration(30).maxduration(30).build()),
                        tuple("imp_id-2", Video.builder().minduration(45).maxduration(45).build()));
    }

    @Test
    public void downgradeShouldReturnEmptyListForDynamicWhenCountIsZero() {
        final Imp imp = givenImp(givenVideo(video -> video
                .poddur(60)
                .maxseq(null)
                .rqddurs(null)
                .minduration(null)
                .maxduration(null)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).containsExactly(imp);
    }

    @Test
    public void downgradeShouldCalculateDynamicCountFromMaxseq() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video
                .poddur(60)
                .maxseq(5)
                .minduration(10)
                .maxduration(15)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(5).extracting(Imp::getId)
                .containsExactly("imp_id-0", "imp_id-1", "imp_id-2", "imp_id-3", "imp_id-4");
        assertThat(result).hasSize(5)
                .extracting(Imp::getVideo)
                .allSatisfy(video -> Video.builder().minduration(10).maxduration(15).build());
    }

    @Test
    public void downgradeShouldCalculateDynamicCountFromMinRqddurs() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video
                .poddur(60)
                .maxseq(null)
                .rqddurs(asList(30, 10, 20))
                .minduration(10)
                .maxduration(15)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(6)
                .extracting(Imp::getId)
                .containsExactly("imp_id-0", "imp_id-1", "imp_id-2", "imp_id-3", "imp_id-4", "imp_id-5");
        assertThat(result).hasSize(6)
                .extracting(Imp::getVideo)
                .allSatisfy(video -> Video.builder().minduration(10).maxduration(15).build());
    }

    @Test
    public void downgradeShouldCalculateDynamicCountFromMinDuration() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video
                .poddur(60)
                .maxseq(null)
                .rqddurs(null)
                .minduration(15)
                .maxduration(20)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(4)
                .extracting(Imp::getId)
                .containsExactly("imp_id-0", "imp_id-1", "imp_id-2", "imp_id-3");
        assertThat(result).hasSize(4)
                .extracting(Imp::getVideo)
                .allSatisfy(video -> Video.builder().minduration(15).maxduration(20).build());
    }

    @Test
    public void downgradeShouldCalculateDynamicCountFromMaxDuration() {
        // given
        final Imp imp = givenImp(givenVideo(video -> video
                .poddur(60)
                .maxseq(null)
                .rqddurs(null)
                .minduration(null)
                .maxduration(20)));

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(3)
                .extracting(Imp::getId)
                .containsExactly("imp_id-0", "imp_id-1", "imp_id-2");
        assertThat(result).hasSize(3)
                .extracting(Imp::getVideo)
                .allSatisfy(video -> Video.builder().minduration(null).maxduration(20).build());
    }

    @Test
    public void downgradeShouldDowngradeBothVideoAndAudioPodsInSameImp() {
        // given
        final Video video = givenVideo(v -> v.poddur(null).rqddurs(asList(15, 20)));
        final Audio audio = givenAudio(a -> a.poddur(30).maxseq(3).minduration(10).maxduration(30));
        final Imp imp = Imp.builder().id(IMP_ID).video(video).audio(audio).build();

        // when
        final List<Imp> result = target.downgrade(imp, BIDDER_NAME, bidderAliases);

        // then
        assertThat(result).hasSize(5)
                .extracting(Imp::getId, Imp::getVideo, Imp::getAudio)
                .containsExactly(
                        tuple("imp_id-0", Video.builder().minduration(15).maxduration(15).build(), null),
                        tuple("imp_id-1", Video.builder().minduration(20).maxduration(20).build(), null),
                        tuple("imp_id-2", null, Audio.builder().minduration(10).maxduration(30).build()),
                        tuple("imp_id-3", null, Audio.builder().minduration(10).maxduration(30).build()),
                        tuple("imp_id-4", null, Audio.builder().minduration(10).maxduration(30).build()));
    }

    private Imp givenImp(Video video) {
        return Imp.builder().id(IMP_ID).video(video).build();
    }

    private Video givenVideo(UnaryOperator<Video.VideoBuilder> customizer) {
        final Video.VideoBuilder builder = Video.builder().podid(1);
        return customizer.apply(builder).build();
    }

    private Audio givenAudio(UnaryOperator<Audio.AudioBuilder> customizer) {
        final Audio.AudioBuilder builder = Audio.builder().podid(1);
        return customizer.apply(builder).build();
    }
}
