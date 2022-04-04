package org.prebid.server.bidder;

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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.ArrayList;
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

    private AuctionContext auctionContext;

    @Before
    public void setUp() {
        bidderMediaTypeProcessor = new BidderMediaTypeProcessor(bidderCatalog);
        auctionContext = givenAuctionContext();
    }

    @Test
    public void processShouldReturnNullAndErrorIfBidderNotSupportAnyMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), emptyList()));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(MediaType.BANNER));

        // when
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result).isNull();
        assertThatContainsExactlyErrors("Bidder " + BIDDER + " does not support any media types");
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
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result).isEqualTo(bidRequest);
        assertThatContainsExactlyErrors();
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
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result).isEqualTo(bidRequest);
        assertThatContainsExactlyErrors();
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
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result)
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(MediaType.AUDIO), givenImp(MediaType.BANNER));
        assertThatContainsExactlyErrors();
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
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result)
                .extracting(BidRequest::getImp)
                .asList()
                .containsExactly(givenImp(MediaType.BANNER, MediaType.AUDIO));
        assertThatContainsExactlyErrors("Imp " + null + " does not have a supported media type for the " + BIDDER
                + "and has been removed from the request for this bidder");
    }

    @Test
    public void processShouldReturnNullIfRequestDoesNotContainsAnyImpWithSupportedMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(MediaType.BANNER, MediaType.AUDIO)));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenImp(MediaType.VIDEO),
                givenImp(MediaType.NATIVE));

        // when
        final BidRequest result = bidderMediaTypeProcessor.process(bidRequest, BIDDER, auctionContext);

        //then
        assertThat(result).isNull();
        assertThatContainsExactlyErrors(
                "Imp " + null + " does not have a supported media type for the " + BIDDER
                        + "and has been removed from the request for this bidder",
                "Imp " + null + " does not have a supported media type for the " + BIDDER
                        + "and has been removed from the request for this bidder",
                "Bid request contains 0 impressions after filtering for " + BIDDER);
    }

    private static BidderInfo givenBidderInfo(List<MediaType> appMediaTypes, List<MediaType> siteMediaType) {
        return BidderInfo.create(
                true,
                false,
                "endpoint",
                null,
                "maintainerEmail",
                appMediaTypes,
                siteMediaType,
                emptyList(),
                0,
                false,
                false);
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

    private static AuctionContext givenAuctionContext() {
        return AuctionContext.builder().prebidErrors(new ArrayList<>()).build();
    }

    private void assertThatContainsExactlyErrors(String... messages) {
        assertThat(auctionContext.getPrebidErrors()).containsExactly(messages);
    }
}
