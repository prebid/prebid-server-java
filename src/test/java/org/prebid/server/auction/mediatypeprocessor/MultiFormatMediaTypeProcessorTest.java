package org.prebid.server.auction.mediatypeprocessor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.BidderAliases;
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

public class MultiFormatMediaTypeProcessorTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;

    private MultiFormatMediaTypeProcessor target;

    @BeforeEach
    public void setUp() {
        target = new MultiFormatMediaTypeProcessor(bidderCatalog);
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnSameBidRequestIfMultiFormatSupported() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(true));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(BANNER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, null);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameBidRequestIfPreferredMediaTypeNotSpecified() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(BANNER, VIDEO));
        final Account account = givenAccount(null);

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnImpWithPreferredMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(BANNER, VIDEO, AUDIO, NATIVE));
        final Account account = givenAccount(Map.of(BIDDER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest())
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

        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .biddercontrols(bidderControls)
                        .build())),
                givenImp(BANNER, VIDEO, AUDIO, NATIVE));

        final Account account = givenAccount(Map.of("resolvedBidderName", AUDIO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .containsExactly(givenImp(VIDEO));
        assertThat(result.getBidRequest())
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBiddercontrols)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldSkipImpsWithSingleMediaType() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(false));
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(BANNER),
                givenImp(VIDEO),
                givenImp(BANNER, VIDEO, AUDIO, NATIVE),
                givenImp(AUDIO),
                givenImp(NATIVE));
        final Account account = givenAccount(Map.of(BIDDER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest())
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
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                givenImp(BANNER, AUDIO),
                givenImp(BANNER, VIDEO),
                givenImp(BANNER, NATIVE),
                givenImp(VIDEO, AUDIO),
                givenImp(VIDEO, NATIVE),
                givenImp(AUDIO, NATIVE));
        final Account account = givenAccount(Map.of(BIDDER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest())
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
        final BidRequest bidRequest = givenBidRequest(identity(), givenImp(BANNER, AUDIO, NATIVE));
        final Account account = givenAccount(Map.of(BIDDER, VIDEO));

        // when
        final MediaTypeProcessingResult result = target.process(bidRequest, BIDDER, bidderAliases, account);

        // then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Imp null does not have a media type after filtering"
                        + " and has been removed from the request for this bidder."),
                BidderError.badInput("Bid request contains 0 impressions after filtering."));
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
                false,
                false,
                CompressionType.NONE,
                Ortb.of(multiFormatSupported));
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

    private static Account givenAccount(Map<String, MediaType> bidderToPreferredMediaType) {
        return Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .preferredMediaTypes(bidderToPreferredMediaType)
                        .build())
                .build();
    }
}
