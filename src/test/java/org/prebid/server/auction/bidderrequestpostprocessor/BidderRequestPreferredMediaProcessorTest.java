package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
public class BidderRequestPreferredMediaProcessorTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;

    private BidderRequestPreferredMediaProcessor target;

    @BeforeEach
    public void setUp() {
        target = new BidderRequestPreferredMediaProcessor(bidderCatalog);
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnSameBidRequestIfMultiFormatSupported() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(true));
        final BidderRequest bidderRequest = givenBidderRequest(identity(), givenImp(BANNER, VIDEO));

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isEqualTo(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameBidRequestIfPreferredMediaTypeNotSpecified() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidderRequest bidderRequest = givenBidderRequest(identity(), givenImp(BANNER, VIDEO));
        final AuctionContext auctionContext = givenAuctionContext(null);

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue()).isEqualTo(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnImpWithPreferredMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidderRequest bidderRequest = givenBidderRequest(identity(), givenImp(BANNER, VIDEO, AUDIO, NATIVE));
        final AuctionContext auctionContext = givenAuctionContext(Map.of(BIDDER, VIDEO));

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(givenImp(VIDEO));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldUseRequestLevelPreferredMediaTypeFirst() {
        // given
        given(bidderAliases.resolveBidder(BIDDER)).willReturn("resolvedBidderName");
        given(bidderCatalog.bidderInfoByName("resolvedBidderName")).willReturn(givenBidderInfo(false));

        final ObjectNode bidderControls = mapper.createObjectNode();
        bidderControls.putObject(BIDDER).put("prefmtype", "video");

        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .biddercontrols(bidderControls)
                        .build())),
                givenImp(BANNER, VIDEO, AUDIO, NATIVE));

        final AuctionContext auctionContext = givenAuctionContext(Map.of("resolvedBidderName", AUDIO));

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(givenImp(VIDEO));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldUseRequestLevelPreferredMediaTypeFirstCaseInsensitive() {
        // given
        given(bidderAliases.resolveBidder(BIDDER)).willReturn("resolvedBidderName");
        given(bidderCatalog.bidderInfoByName("resolvedBidderName")).willReturn(givenBidderInfo(false));

        final ObjectNode bidderControls = mapper.createObjectNode();
        bidderControls.putObject(BIDDER.toUpperCase()).put("prefmtype", "video");

        final BidderRequest bidderRequest = givenBidderRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .biddercontrols(bidderControls)
                        .build())),
                givenImp(BANNER, VIDEO, AUDIO, NATIVE));

        final AuctionContext auctionContext = givenAuctionContext(Map.of("resolvedBidderName", AUDIO));

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(givenImp(VIDEO));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldSkipImpsWithSingleMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidderRequest bidderRequest = givenBidderRequest(
                identity(),
                givenImp(BANNER),
                givenImp(VIDEO),
                givenImp(BANNER, VIDEO, AUDIO, NATIVE),
                givenImp(AUDIO),
                givenImp(NATIVE));
        final AuctionContext auctionContext = givenAuctionContext(Map.of(BIDDER, VIDEO));

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(
                        givenImp(BANNER),
                        givenImp(VIDEO),
                        givenImp(VIDEO),
                        givenImp(AUDIO),
                        givenImp(NATIVE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldFilterMultiFormatImpsWithoutPreferredMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidderRequest bidderRequest = givenBidderRequest(
                identity(),
                givenImp(BANNER, AUDIO),
                givenImp(BANNER, VIDEO),
                givenImp(BANNER, NATIVE),
                givenImp(VIDEO, AUDIO),
                givenImp(VIDEO, NATIVE),
                givenImp(AUDIO, NATIVE));
        final AuctionContext auctionContext = givenAuctionContext(Map.of(BIDDER, VIDEO));

        // when
        final BidderRequestPostProcessingResult result =
                target.process(bidderRequest, bidderAliases, auctionContext).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(
                        givenImp(VIDEO),
                        givenImp(VIDEO),
                        givenImp(VIDEO));
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp null does not have a media type after filtering"
                        + " and has been removed from the request for this bidder."),
                BidderError.badInput("Imp null does not have a media type after filtering"
                        + " and has been removed from the request for this bidder."),
                BidderError.badInput("Imp null does not have a media type after filtering"
                        + " and has been removed from the request for this bidder."));
    }

    @Test
    public void processShouldRejectEmptyRequestAfterFiltering() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidderRequest bidderRequest = givenBidderRequest(identity(), givenImp(BANNER, AUDIO, NATIVE));
        final AuctionContext auctionContext = givenAuctionContext(Map.of(BIDDER, VIDEO));

        // when
        final Future<BidderRequestPostProcessingResult> result =
                target.process(bidderRequest, bidderAliases, auctionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .asInstanceOf(InstanceOfAssertFactories.type(BidderRequestRejectedException.class))
                .satisfies(e -> {
                    assertThat(e.getRejectionReason())
                            .isEqualTo(BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getErrors()).containsExactly(
                            BidderError.badInput("Imp null does not have a media type after filtering"
                                    + " and has been removed from the request for this bidder."),
                            BidderError.badInput("Bid request contains 0 impressions after filtering."));
                });
    }

    private static BidderInfo givenBidderInfo(boolean multiFormatSupported) {
        return BidderInfo.create(
                true,
                OrtbVersion.ORTB_2_6,
                false,
                "endpoint",
                null,
                "maintainerEmail",
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                emptyList(),
                false,
                false,
                CompressionType.NONE,
                Ortb.of(multiFormatSupported),
                0L);
    }

    private static BidderRequest givenBidderRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                                    Imp... imps) {

        return BidderRequest.builder()
                .bidder(BIDDER)
                .bidRequest(bidRequestCustomizer.apply(BidRequest.builder()).imp(asList(imps)).build())
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

    private static AuctionContext givenAuctionContext(Map<String, MediaType> bidderToPreferredMediaType) {
        return AuctionContext.builder()
                .account(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .preferredMediaTypes(bidderToPreferredMediaType)
                                .build())
                        .build())
                .build();
    }
}
