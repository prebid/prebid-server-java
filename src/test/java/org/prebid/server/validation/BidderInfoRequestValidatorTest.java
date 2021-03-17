package org.prebid.server.validation;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.settings.bidder.CapabilitiesInfo;
import org.prebid.server.settings.bidder.PlatformInfo;
import org.prebid.server.validation.model.ValueValidationResult;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderInfoRequestValidatorTest {

    private static final String BANNER = "banner";
    private static final String VIDEO = "video";
    private static final String NATIVE = "native";
    private static final String AUDIO = "audio";

    private static final String IMP_ID = "impId";

    private BidderInfoRequestValidator target;

    @Before
    public void setUp() {
        target = new BidderInfoRequestValidator();
    }

    @Test
    public void shouldReturnUnchangedResultWhenValidationIsNotRequired() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final PlatformInfo platformInfo = PlatformInfo.create(emptyList());
        final CapabilitiesInfo capabilitiesInfo = new CapabilitiesInfo(false, platformInfo, platformInfo);

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        assertThat(result).isEqualTo(ValueValidationResult.success(bidRequest));
    }

    @Test
    public void shouldReturnEmptyResultAndErrorWhenAppIsProvidedWithoutSupport() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CapabilitiesInfo capabilitiesInfo = capabilitiesInfo(emptyList(), emptyList());

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        assertThat(result)
                .isEqualTo(ValueValidationResult.error("App for bidder is not supported, request will be skipped"));
    }

    @Test
    public void shouldReturnEmptyResultAndErrorWhenSiteIsProvidedWithoutSupport() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().build()).app(null),
                Function.identity());
        final CapabilitiesInfo capabilitiesInfo = capabilitiesInfo(emptyList(), emptyList());

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        assertThat(result.getValue()).isNull();
        assertThat(result.getErrors())
                .containsOnly("Site for bidder is not supported, request will be skipped");
    }

    @Test
    public void shouldReturnEmptyResultErrorAndWarningWhenImpHasNotSupportedTypes() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(Function.identity()));
        final CapabilitiesInfo capabilitiesInfo = capabilitiesInfo(singletonList(VIDEO), emptyList());

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        assertThat(result.getValue()).isNull();
        assertThat(result.getWarnings())
                .containsOnly(String.format("Imp with id %s doesn't contain media types supported by the "
                        + "bidder, and will be skipped", IMP_ID));
        assertThat(result.getErrors())
                .containsOnly("Bid request didn't contain media types supported by the bidder");
    }

    @Test
    public void shouldReturnWarningWhenImpMediaTypeIsUnsupported() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(builder -> builder
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())
                        .audio(Audio.builder().build())
                        .xNative(Native.builder().build())),
                givenImp(builder -> builder.id("impId2").video(Video.builder().build())));
        final CapabilitiesInfo capabilitiesInfo = capabilitiesInfo(singletonList(VIDEO), emptyList());

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        final BidRequest expectedBidRequest = givenBidRequest(
                givenImp(builder -> builder.video(Video.builder().build())),
                givenImp(builder -> builder.id("impId2").video(Video.builder().build())));
        assertThat(result.getValue()).isEqualTo(expectedBidRequest);
        assertThat(result.getWarnings())
                .containsOnly(
                        String.format("Imp with id %s uses %s, but this bidder doesn't this type", IMP_ID, BANNER),
                        String.format("Imp with id %s uses %s, but this bidder doesn't this type", IMP_ID, AUDIO),
                        String.format("Imp with id %s uses %s, but this bidder doesn't this type", IMP_ID, NATIVE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void shouldReturnUnchangedWhenThereAreNoWarningsOrErrors() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(builder -> builder.video(Video.builder().build())),
                givenImp(builder -> builder.audio(Audio.builder().build())),
                givenImp(builder -> builder.video(Video.builder().build())),
                givenImp(builder -> builder.video(Video.builder().build()))
        );
        final CapabilitiesInfo capabilitiesInfo = capabilitiesInfo(Arrays.asList(VIDEO, AUDIO), emptyList());

        // when
        final ValueValidationResult<BidRequest> result = target.validate(bidRequest, capabilitiesInfo);

        // then
        assertThat(result.getValue()).isEqualTo(bidRequest);
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    private static CapabilitiesInfo capabilitiesInfo(List<String> appMediaTypes, List<String> siteMediaTypes) {
        return new CapabilitiesInfo(true, PlatformInfo.create(appMediaTypes), PlatformInfo.create(siteMediaTypes));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .app(App.builder().build())
                .imp(Arrays.asList(imps))
                .build();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id(IMP_ID)).build();
    }
}
