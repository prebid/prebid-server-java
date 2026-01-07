package org.prebid.server.auction.bidderrequestpostprocessor;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.Result;
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
public class BidderRequestMediaFilterTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;

    private BidderRequestMediaFilter target;

    @BeforeEach
    public void setUp() {
        target = new BidderRequestMediaFilter(bidderCatalog);
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnRejectedResultAndErrorIfBidderDoesNotSupportAnyMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), emptyList(), emptyList()));
        final BidderRequest bidderRequest = givenBidderRequest(identity(), givenImp(BANNER));

        // when
        final Future<Result<BidderRequest>> result = target.process(bidderRequest, bidderAliases, null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .asInstanceOf(InstanceOfAssertFactories.type(BidderRequestRejectedException.class))
                .satisfies(e -> {
                    assertThat(e.getRejectionReason())
                            .isEqualTo(BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getErrors()).containsExactly(
                            BidderError.badInput("Bidder does not support any media types."));
                });
    }

    @Test
    public void processShouldUseAppMediaTypesIfAppPresent() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(singletonList(BANNER), singletonList(AUDIO), singletonList(NATIVE)));
        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.app(App.builder().build()),
                givenImp(BANNER));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isEqualTo(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), List.of(BANNER, AUDIO), emptyList()));
        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.site(Site.builder().build()),
                givenImp(AUDIO, NATIVE),
                givenImp(BANNER, VIDEO));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(givenImp(AUDIO), givenImp(BANNER));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveImpWithOnlyUnsupportedMediaTypes() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER))
                .willReturn(givenBidderInfo(emptyList(), asList(BANNER, AUDIO), emptyList()));
        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.site(Site.builder().build()),
                givenImp(VIDEO, NATIVE),
                givenImp(BANNER, AUDIO));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
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
        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.site(Site.builder().build()),
                givenImp(VIDEO),
                givenImp(NATIVE));

        // when
        final Future<Result<BidderRequest>> result = target.process(bidderRequest, bidderAliases, null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .asInstanceOf(InstanceOfAssertFactories.type(BidderRequestRejectedException.class))
                .satisfies(e -> {
                    assertThat(e.getRejectionReason())
                            .isEqualTo(BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getErrors()).containsExactly(
                            BidderError.badInput("Imp null does not have a supported media type"
                                    + " and has been removed from the request for this bidder."),
                            BidderError.badInput("Imp null does not have a supported media type"
                                    + " and has been removed from the request for this bidder."),
                            BidderError.badInput("Bid request contains 0 impressions after filtering."));
                });
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
                Ortb.of(false),
                0L);
    }

    private static BidderRequest givenBidderRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                                    Imp... imps) {

        return BidderRequest.builder()
                .bidRequest(bidRequestCustomizer.apply(BidRequest.builder()).imp(asList(imps)).build())
                .bidder(BIDDER)
                .build();
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
