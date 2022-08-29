package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class BidderMediaTypeProcessorTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private BidderMediaTypeProcessor bidderMediaTypeProcessor;

    @Before
    public void setUp() {
        bidderMediaTypeProcessor = new BidderMediaTypeProcessor(bidderCatalog);
    }

    @Test
    public void processShouldReturnRejectedResultAndErrorIfBidderNotSupportAnyMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), emptyList()));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(MediaType.BANNER));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Bidder does not support any media types."));
    }

    @Test
    public void processShouldUseAppMediaTypesIfAppPresent() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(singletonList(MediaType.BANNER), singletonList(MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().build()),
                givenImp(MediaType.BANNER));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldUseSiteMediaTypesIfSitePresent() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(singletonList(MediaType.BANNER), singletonList(MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(MediaType.AUDIO));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(MediaType.BANNER, MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(MediaType.AUDIO, MediaType.NATIVE),
                givenImp(MediaType.BANNER, MediaType.VIDEO));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(MediaType.AUDIO), givenImp(MediaType.BANNER));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveImpWithOnlyUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(MediaType.BANNER, MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(MediaType.VIDEO, MediaType.NATIVE),
                givenImp(MediaType.BANNER, MediaType.AUDIO));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(MediaType.BANNER, MediaType.AUDIO));
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."));
    }

    @Test
    public void processShouldReturnRejectedResultIfRequestDoesNotContainsAnyImpWithSupportedMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(MediaType.BANNER, MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(MediaType.VIDEO),
                givenImp(MediaType.NATIVE));

        // when
        final MediaTypeProcessingResult result = bidderMediaTypeProcessor.process(bidRequest, BIDDER);

        //then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."),
                BidderError.badInput("Imp " + null + " does not have a supported media type "
                        + "and has been removed from the request for this bidder."),
                BidderError.badInput("Bid request contains 0 impressions after filtering."));
    }

    private static BidderInfo givenBidderInfo(List<MediaType> appMediaTypes, List<MediaType> siteMediaType) {
        return BidderInfo.create(
                true,
                OrtbVersion.ORTB_2_6,
                false,
                "endpoint",
                null,
                "maintainerEmail",
                appMediaTypes,
                siteMediaType,
                emptyList(),
                0,
                false,
                false,
                CompressionType.NONE);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder()).imp(asList(imps)).build();
    }

    private static Imp givenImp(MediaType... mediaTypes) {
        final Set<MediaType> setOfMediaTypes = Set.of(mediaTypes);
        final Imp.ImpBuilder impBuilder = Imp.builder();

        if (setOfMediaTypes.contains(MediaType.BANNER)) {
            impBuilder.banner(Banner.builder().build());
        }
        if (setOfMediaTypes.contains(MediaType.VIDEO)) {
            impBuilder.video(Video.builder().build());
        }
        if (setOfMediaTypes.contains(MediaType.AUDIO)) {
            impBuilder.audio(Audio.builder().build());
        }
        if (setOfMediaTypes.contains(MediaType.NATIVE)) {
            impBuilder.xNative(Native.builder().build());
        }

        return impBuilder.build();
    }
}
