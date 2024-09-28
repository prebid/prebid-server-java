package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.prebid.server.spring.config.bidder.model.MediaType.AUDIO;
import static org.prebid.server.spring.config.bidder.model.MediaType.BANNER;
import static org.prebid.server.spring.config.bidder.model.MediaType.NATIVE;
import static org.prebid.server.spring.config.bidder.model.MediaType.VIDEO;

@ExtendWith(MockitoExtension.class)
public class BidderMediaTypeProcessorTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;

    private BidderMediaTypeProcessor target;

    @BeforeEach
    public void setUp() {
        target = new BidderMediaTypeProcessor(bidderCatalog);
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnRejectedResultAndErrorIfBidderNotSupportAnyMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), emptyList(), emptyList()));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(BANNER));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Bidder does not support any media types."));
    }

    @Test
    public void processShouldUseAppMediaTypesIfAppPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.app(App.builder().build()), givenImp(BANNER));
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(singletonList(BANNER), singletonList(AUDIO), singletonList(NATIVE)));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), List.of(BANNER, AUDIO), emptyList()));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(AUDIO, NATIVE),
                givenImp(BANNER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(AUDIO), givenImp(BANNER));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveImpWithOnlyUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(BANNER, AUDIO), emptyList()));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(VIDEO, NATIVE),
                givenImp(BANNER, AUDIO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(BANNER, AUDIO));
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."));
    }

    @Test
    public void processShouldReturnRejectedResultIfRequestDoesNotContainsAnyImpWithSupportedMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(BANNER, AUDIO), emptyList()));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(VIDEO),
                givenImp(NATIVE));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."),
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."),
                BidderError.badInput("Bid request contains 0 impressions after filtering."));
    }

    private static BidderInfo givenBidderInfo(List<MediaType> appMediaTypes,
                                              List<MediaType> siteMediaType,
                                              List<MediaType> doohMediaType) {
        return BidderInfo.create(
                true,
                OrtbVersion.ORTB_2_6,
                false,
                "endpoint",
                null,
                "maintainerEmail",
                appMediaTypes,
                siteMediaType,
                doohMediaType,
                emptyList(),
                0,
                null,
                false,
                false,
                CompressionType.NONE,
                Ortb.of(false));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder()).imp(asList(imps)).build();
    }

    private static Imp givenImp(MediaType... mediaTypes) {
        final Set<MediaType> setOfMediaTypes = Set.of(mediaTypes);
        final Imp.ImpBuilder impBuilder = Imp.builder();

        if (setOfMediaTypes.contains(BANNER)) {
            impBuilder.banner(Banner.builder().build());
        }
        if (setOfMediaTypes.contains(VIDEO)) {
            impBuilder.video(Video.builder().build());
        }
        if (setOfMediaTypes.contains(AUDIO)) {
            impBuilder.audio(Audio.builder().build());
        }
        if (setOfMediaTypes.contains(NATIVE)) {
            impBuilder.xNative(Native.builder().build());
        }

        return impBuilder.build();
    }
}
