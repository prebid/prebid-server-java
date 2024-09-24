package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredBidResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class ImpValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Mock
    private BidderParamValidator bidderParamValidator;
    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;

    private ImpValidator target;

    @BeforeEach
    public void setUp() {
        target = new ImpValidator(bidderParamValidator, bidderCatalog, jacksonMapper);

        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenImpIdNull() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder().id(null).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenImpIdEmptyString() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder().id("").build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenNoneOfMediaTypeIsPresent() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .video(null)
                .audio(null)
                .banner(null)
                .xNative(null)
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0] must contain at least one of \"banner\", \"video\", \"audio\", or "
                        + "\"native\"");

    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenVideoAttributeIsPresentButVideoMimesMissed() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .video(Video.builder().mimes(emptyList()).build())
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].video.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenAudioAttributePresentButAudioMimesMissed() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .audio(Audio.builder().mimes(emptyList()).build())
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].audio.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasNullFormatAndNoSizes() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .format(null)
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnEmptyValidationMessagesWhenBannerHasNullFormatAndValidSizes()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .format(null)
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoSizes() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoHeight() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoWidth() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroHeight() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(0)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasZeroHeight() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(0)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldNotReturnValidationMessageForSizesIfImpIsInterstitial() throws ValidationException {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .instl(1)
                .banner(Banner.builder()
                        .w(0)
                        .h(300)
                        .format(singletonList(Format.builder().w(1).h(1).build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroWidth() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(0)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasZeroWidth() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(0)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeWidth() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(-300)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasNegativeWidth() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(-300)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeHeight() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(-300)
                        .w(600)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerHasNegativeHeight() {
        // given
        final List<Imp> givenImps = singletonList(Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(-300)
                        .w(600)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatHWAndRatiosPresent() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).w(2).wmin(3).wratio(4).hratio(5));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatHeightWeightAndOneOfRatiosPresent() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).w(2).hratio(5));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosAndOneOfSizesPresent() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).wmin(3).wratio(4).hratio(5));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpsShouldReturnEmptyValidationMessagesWhenBannerFormatSizesSpecifiedOnly()
            throws ValidationException {

        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).w(2));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnEmptyValidationMessagesWhenBannerFormatRatiosSpecifiedOnly()
            throws ValidationException {

        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(3).wratio(4).hratio(5));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatSizesAndRatiosPresent() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(), identity());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] should define *either* {w, h} (for static size "
                        + "requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsNull() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(null).w(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsZero() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(0).w(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsNull() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).w(null));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsZero() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(1).w(0));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatHeightIsNegative() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(-1).w(2));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatWidthIsNegative() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.h(2).w(-1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNull() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(null).wratio(2).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define"
                        + " a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsZero() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(0).wratio(2).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNegative() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(-1).wratio(2).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNull() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(null).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\","
                        + " and \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsZero() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(0).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNegative() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(-1).hratio(1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNull() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(5).hratio(null));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsZero() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(5).hratio(0));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNegative() {
        // given
        final List<Imp> givenImps = overwriteBannerFormatInFirstImp(givenValidImps(),
                formatBuilder -> formatBuilder.wmin(1).wratio(5).hratio(-1));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenPmpDealIdIsNull() {
        // given
        final List<Imp> givenImps = overwritePmpFirstDealInFirstImp(givenValidImps(),
                dealBuilder -> dealBuilder.id(null));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenPmpDealIdIsEmptyString() {
        // given
        final List<Imp> givenImps = overwritePmpFirstDealInFirstImp(givenValidImps(),
                dealBuilder -> dealBuilder.id(""));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpsShouldThrowExceptionWhenNativeRequestEmpty() {
        // given
        final List<Imp> givenImps = givenImps(identity());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native contains empty request value");
    }

    @Test
    public void validateImpsShouldThrowExceptionWhenNativeRequestMalformed() {
        // given
        final List<Imp> givenImps = givenImps(nativeCustomizer -> nativeCustomizer.request("broken-request"));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("Error while parsing request.imp[0].native.request: JsonParseException:");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithoutErrorsForNativeSpecificContextTypes()
            throws Exception {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(500).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenContextTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(323));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.context is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenContextSubTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(100));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.contextsubtype is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateImpsShouldReturnErrorWhenContextSubTypeAndContextTypeOutOfPossibleContentValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(11));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.context is 2, but contextsubtype is 11. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateImpsShouldReturnErrorWhenContextSubTypeAndContextTypeOutOfPossibleSocialValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(3).contextsubtype(21));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.context is 3, but contextsubtype is 21. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateImpsShouldReturnErrorWhenContextSubTypeAndContextTypeOutOfPossibleProductValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(31));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.context is 2, but contextsubtype is 31. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithEmptyErrorWhenContextSubTypeAndContextTypeValid()
            throws Exception {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithEmptyErrorWhenContextIsNull() throws Exception {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(null).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithEmptyErrorWhenSubTypeContextIsNull() throws Exception {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(null).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenEventTrackersOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(323).build())).assets(singletonList(Asset.builder().build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.eventtrackers[0].event is invalid. See section 7.6: "
                        + "https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenEventTrackerEmptyMethods()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).build())).assets(singletonList(Asset.builder().build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.eventtrackers[0].method is required. "
                        + "See section 7.7: https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenEventTrackerInvalidMethod()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).methods(singletonList(3)).build())).assets(singletonList(Asset.builder().build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.eventtrackers[0].methods[0] is invalid. "
                        + "See section 7.7: https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithEmptyErrorWhenValidEventTracker() throws Exception {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).methods(singletonList(2)).build())).assets(singletonList(Asset.builder().build())));
        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithEmptyErrorWhenEventTrackerHasSpecificType()
            throws Exception {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(500).methods(singletonList(2)).build())).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithoutErrorsForNativeSpecificPlacementTypes()
            throws Exception {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.plcmttype(500).assets(singletonList(Asset.builder().build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenPlacementTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.plcmttype(323));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.plcmttype is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=40");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenAssetsContainsZeroElements()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(emptyList()));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets must be an array containing at least one object");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenElementInAssetsHasWhichIsNotUnique()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(asList(
                        Asset.builder().id(1).build(),
                        // this should get ID set on second iteration (i = 1) and result in conflict with previous id
                        Asset.builder().build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[1].id is already being used by another asset. "
                        + "Each asset ID must be unique.");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndImage()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .img(ImageObject.builder().build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndVideo()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .video(VideoObject.builder().build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndData()
            throws JsonProcessingException {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .data(DataObject.builder().build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenIndividualAssetHasImageAndVideo()
            throws JsonProcessingException {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder().build())
                        .video(VideoObject.builder().build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].native.request.assets[0] must define at most one of {title, img, video, data}");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenIndividualAssetHasImageAndData()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder().build())
                        .data(DataObject.builder().build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(
                        "request.imp[0].native.request.assets[0] must define at most one of {title, img, video, data}");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenHasZeroTitleLen() throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().len(0).build()).build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].title.len must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenHasNullTitleLen() throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().len(null).build()).build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].title.len must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenDataTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .data(DataObject.builder().type(100).build()).build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].data.type is invalid. See section 7.4: "
                        + "https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=40");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithoutErrorsWhenDataHasSpecicNativeTypes()
            throws Exception {

        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .data(DataObject.builder().type(500).build()).build())));

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMimes()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder().mimes(emptyList()).build()).build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.mimes must be an array with at least one"
                        + " MIME type");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMinDuration()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(null)
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.minduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasMinDurationLessThanOne()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(0)
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.minduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMaxDuration()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(null)
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasMaxDurationLessThanOne()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyProtocols()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .protocols(emptyList())
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnValidationResultWithErrorWhenNativeVideoProtocolsOutOfPossibleValues()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .protocols(singletonList(20))
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateImpsShouldReturnEmptyValidationMessagesWhenNativeVideoIsValid()
            throws JsonProcessingException {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(2)
                                .protocols(singletonList(0))
                                .build())
                        .build())));

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].native.request.assets[0].video.protocols[0] must be in the range [1, 10]."
                        + " Got 0");
    }

    @Test
    public void validateImpsShouldUpdateNativeRequestAssetsIds() throws Exception {
        // given
        final List<Imp> givenImps = givenNativeImps(nativeReqCustomizer ->
                nativeReqCustomizer.assets(asList(Asset.builder().build(), Asset.builder().build())));

        // when
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());

        assertThat(givenImps).hasSize(1)
                .extracting(Imp::getXNative).doesNotContainNull()
                .extracting(Native::getRequest).doesNotContainNull()
                .extracting(req -> mapper.readValue(req, Request.class))
                .flatExtracting(Request::getAssets)
                .flatExtracting(Asset::getId)
                .containsOnly(0, 1);
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenMetricTypeNullOrEmpty() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .metric(singletonList(Metric.builder().type(null).build())).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Missing request.imp[0].metric[0].type");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenMetricValueIsNotValid() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .metric(singletonList(Metric.builder().type("viewability").value(2.0f).build())).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].metric[0].value must be in the range [0.0, 1.0]");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenNoImpExtPrebidPresent() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder().ext(null).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid must be defined");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenImpExtPrebidIsNotObject() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", "test")))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid must an object type");
    }

    @Test
    public void validateImpsShouldReturnValidationMessagesWhenExtImpPrebidBidderWasNotDefined() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("attr", "value")))).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.bidder must be defined");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenImpExtPrebidBiddersNotDefinedForStoredBidResponse() {
        // given
        final ObjectNode prebid = mapper.valueToTree(ExtImpPrebid.builder()
                .storedBidResponse(singletonList(ExtStoredBidResponse.of("bidder", "id")))
                .storedAuctionResponse(ExtStoredAuctionResponse.of("id", null, null))
                .build());

        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", prebid))).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.bidder should be defined for storedbidresponse");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenStoredBidResponseBidderMissed() {
        // given
        final ObjectNode prebid = mapper.valueToTree(ExtImpPrebid.builder()
                .storedBidResponse(singletonList(ExtStoredBidResponse.of(null, "id")))
                .bidder(mapper.createObjectNode().put("rubicon", 1))
                .build());

        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", prebid))).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.storedbidresponse.bidder was not defined");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenStoredBidResponseIdMissed() {
        // given
        final ObjectNode prebid = mapper.valueToTree(ExtImpPrebid.builder()
                .storedBidResponse(singletonList(ExtStoredBidResponse.of("bidder", null)))
                .bidder(mapper.createObjectNode().put("rubicon", 1))
                .build());

        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", prebid)))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Id was not defined for request.imp[0].ext.prebid.storedbidresponse.id");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenStoredBidResponseBidderIsNotValidBidder() {
        // given
        final ObjectNode prebid = mapper.valueToTree(ExtImpPrebid.builder()
                .storedBidResponse(singletonList(ExtStoredBidResponse.of("bidder", "id")))
                .bidder(mapper.createObjectNode().put("rubicon", 1))
                .build());

        given(bidderCatalog.isValidName(eq("bidder"))).willReturn(false);

        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", prebid))).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.storedbidresponse.bidder is not valid bidder");
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenStoredBidResponseBidderIsNotInImpExtPrebidBidder() {
        // given
        final ObjectNode prebid = mapper.valueToTree(ExtImpPrebid.builder()
                .storedBidResponse(singletonList(ExtStoredBidResponse.of("bidder", "id")))
                .bidder(mapper.createObjectNode().put("rubicon", 1))
                .build());

        given(bidderCatalog.isValidName(eq("bidder"))).willReturn(true);

        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", prebid))).build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.storedbidresponse.bidder does not have correspondent"
                        + " bidder parameters");
    }

    @Test
    public void validateImpsShouldReturnEmptyMessagesWhenExtImpPrebidBidderWasMissedAndHasStoredAuctionResponseWas()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("storedauctionresponse",
                        mapper.createObjectNode().put("id", "1"))))).build());

        // when & then
        target.validateImps(givenImps, Collections.emptyMap(), new ArrayList<>());
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenImpExtPrebidBidderIsNotObject() {
        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("bidder", "test"))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.bidder must be an object type");
    }

    @Test
    public void validateImpsShouldReturnWarningAndDropBidderWhenImpExtPrebidBidderIsUnknown()
            throws ValidationException {

        // given
        final List<Imp> givenImps = givenValidImps();
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(false);

        final List<String> debugMessages = new ArrayList<>();

        // when
        target.validateImps(givenImps, Collections.emptyMap(), debugMessages);

        // then
        assertThat(debugMessages)
                .containsExactly("WARNING: request.imp[0].ext.prebid.bidder.rubicon was dropped with a reason: "
                                + "request.imp[0].ext.prebid.bidder contains unknown bidder: rubicon",
                        "WARNING: request.imp[0].ext must contain at least one valid bidder");

        assertThat(givenImps)
                .extracting(Imp::getExt)
                .extracting(impExt -> impExt.get("prebid"))
                .extracting(prebid -> prebid.get("bidder"))
                .containsOnly(mapper.createObjectNode());
    }

    @Test
    public void validateImpsShouldReturnWarningMessageAndDropBidderWhenBidderExtIsInvalid() throws ValidationException {
        // given
        final List<Imp> givenImps = givenValidImps();
        given(bidderParamValidator.validate(any(), any()))
                .willReturn(new LinkedHashSet<>(asList("errorMessage1", "errorMessage2")));

        final List<String> debugMessages = new ArrayList<>();

        // when
        target.validateImps(givenImps, Collections.emptyMap(), debugMessages);

        // then
        assertThat(debugMessages)
                .containsExactly(
                        """
                                WARNING: request.imp[0].ext.prebid.bidder.rubicon was dropped with a reason: \
                                request.imp[0].ext.prebid.bidder.rubicon failed validation.
                                errorMessage1
                                errorMessage2""",
                        "WARNING: request.imp[0].ext must contain at least one valid bidder");

        assertThat(givenImps)
                .extracting(Imp::getExt)
                .extracting(impExt -> impExt.get("prebid"))
                .extracting(prebid -> prebid.get("bidder"))
                .containsOnly(mapper.createObjectNode());
    }

    @Test
    public void validateImpsShouldReturnWarningMessageAndDropBidderWhenImpExtPrebidImpBidderIsUnknown()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid",
                        Map.of("imp", singletonMap("unknownBidder", 0), "bidder", singletonMap("rubicon", 0)))))
                .build());

        given(bidderCatalog.isValidName(eq("unknownBidder"))).willReturn(false);

        final List<String> debugMessages = new ArrayList<>();

        // when
        target.validateImps(givenImps, Collections.emptyMap(), debugMessages);

        // then
        assertThat(debugMessages).containsExactly(
                "WARNING: request.imp[0].ext.prebid.imp.unknownBidder was dropped with the reason: invalid bidder");

        assertThat(givenImps)
                .extracting(Imp::getExt)
                .extracting(impExt -> impExt.get("prebid"))
                .extracting(prebid -> prebid.get("imp"))
                .containsOnly(mapper.createObjectNode());
    }

    @Test
    public void validateImpsShouldReturnNoMessageWhenImpExtPrebidImpBidderIsAlias()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                .ext(mapper.valueToTree(singletonMap("prebid",
                        Map.of("imp", singletonMap("rubiconAlias", 0), "bidder", singletonMap("rubicon", 0)))))
                .build());

        final List<String> debugMessages = new ArrayList<>();

        // when
        target.validateImps(givenImps, Map.of("rubiconAlias", "rubicon"), debugMessages);

        // then
        assertThat(debugMessages).isEmpty();

        assertThat(givenImps)
                .extracting(Imp::getExt)
                .extracting(impExt -> impExt.get("prebid"))
                .extracting(prebid -> prebid.get("imp"))
                .containsOnly(mapper.createObjectNode().put("rubiconAlias", 0));
    }

    @Test
    public void validateImpsShouldReturnValidationMessageWhenExtImpPrebidHasStoredAuctionResponseWithoutId()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                        .ext(mapper.valueToTree(singletonMap("prebid", singletonMap(
                                "storedauctionresponse", mapper.createObjectNode()))))
                .build());

        // when & then
        assertThatThrownBy(() -> target.validateImps(givenImps, Collections.emptyMap(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("request.imp[0].ext.prebid.storedauctionresponse.id should be defined");
    }

    @Test
    public void validateImpsShouldReturnWarningMessageWhenExtImpPrebidHasStoredAuctionResponseSeatBidArr()
            throws ValidationException {

        // given
        final List<Imp> givenImps = singletonList(validImpBuilder()
                        .ext(mapper.valueToTree(singletonMap("prebid", Map.of(
                                "storedauctionresponse", mapper.createObjectNode()
                                        .put("id", "1")
                                        .set("seatbidarr", mapper.createArrayNode())))
                        )).build());

        final List<String> debugMessages = new ArrayList<>();

        // when
        target.validateImps(givenImps, Collections.emptyMap(), debugMessages);

        // then
        assertThat(debugMessages)
                .containsOnly("WARNING: request.imp[0].ext.prebid.storedauctionresponse.seatbidarr "
                        + "is not supported at the imp level");
    }

    // validateImp method tests

    @Test
    public void validateImpShouldReturnValidationMessageWhenImpIdNull() {
        // given
        final Imp givenImp = validImpBuilder().id(null).build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=null] missing required field: \"id\"");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoWidth() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroHeight() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(0)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasZeroHeight() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(0)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenImpIdEmptyString() {
        // given
        final Imp givenImp = validImpBuilder().id("").build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=] missing required field: \"id\"");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenNoneOfMediaTypeIsPresent() {
        // given
        final Imp givenImp = validImpBuilder()
                .video(null)
                .audio(null)
                .banner(null)
                .xNative(null)
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200] must contain at least one of \"banner\", \"video\", \"audio\", or "
                        + "\"native\"");

    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenVideoAttributeIsPresentButVideoMimesMissed() {
        // given
        final Imp givenImp = validImpBuilder()
                .video(Video.builder().mimes(emptyList()).build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].video.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenAudioAttributePresentButAudioMimesMissed() {
        // given
        final Imp givenImp = validImpBuilder()
                .audio(Audio.builder().mimes(emptyList()).build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].audio.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasNullFormatAndNoSizes() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .format(null)
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnEmptyValidationMessagesWhenBannerHasNullFormatAndValidSizes()
            throws ValidationException {

        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .h(250)
                        .format(null)
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        target.validateImp(givenImp);
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoSizes() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoHeight() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .w(300)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldNotReturnValidationMessageForSizesIfImpIsInterstitial() throws ValidationException {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .instl(1)
                .banner(Banner.builder()
                        .w(0)
                        .h(300)
                        .format(singletonList(Format.builder().w(1).h(1).build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        target.validateImp(givenImp);
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroWidth() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(0)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasZeroWidth() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(0)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeWidth() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(-300)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasNegativeWidth() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(600)
                        .w(-300)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeHeight() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(-300)
                        .w(600)
                        .format(emptyList())
                        .build())
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerHasNegativeHeight() {
        // given
        final Imp givenImp = Imp.builder()
                .id("11")
                .banner(Banner.builder()
                        .h(-300)
                        .w(600)
                        .format(singletonList(Format.builder().build()))
                        .build())
                .ext(mapper.valueToTree(
                        singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))))
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=11].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatHWAndRatiosPresent() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).w(2).wmin(3).wratio(4).hratio(5).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatHeightWeightAndOneOfRatiosPresent() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).w(2).hratio(5).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosAndOneOfSizesPresent() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).wmin(3).wratio(4).hratio(5).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateImpShouldReturnEmptyValidationMessagesWhenBannerFormatSizesSpecifiedOnly()
            throws ValidationException {

        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).w(2).build()))
                        .build())
                .build();

        // when & then
        target.validateImp(givenImp);
    }

    @Test
    public void validateImpShouldReturnEmptyValidationMessagesWhenBannerFormatRatiosSpecifiedOnly()
            throws ValidationException {

        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(3).wratio(4).hratio(5).build()))
                        .build())
                .build();

        // when & then
        target.validateImp(givenImp);
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatSizesAndRatiosPresent() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] should define *either* {w, h} (for static size "
                        + "requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(null).w(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsZero() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(0).w(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).w(null).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsZero() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(1).w(0).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatHeightIsNegative() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(-1).w(2).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatWidthIsNegative() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().h(2).w(-1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(null).wratio(2).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define"
                        + " a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsZero() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(0).wratio(2).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNegative() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(-1).wratio(2).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(null).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\","
                        + " and \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsZero() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(0).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNegative() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(-1).hratio(1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(null).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsZero() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(0).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNegative() {
        // given
        final Imp givenImp = validImpBuilder()
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(-1).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenPmpDealIdIsNull() {
        // given
        final Imp givenImp = validImpBuilder()
                .pmp(Pmp.builder()
                        .deals(singletonList(Deal.builder().id(null).build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenPmpDealIdIsEmptyString() {
        // given
        final Imp givenImp = validImpBuilder()
                .pmp(Pmp.builder()
                        .deals(singletonList(Deal.builder().id("").build()))
                        .build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenMetricTypeNullOrEmpty() {
        // given
        final Imp givenImp = validImpBuilder()
                .metric(singletonList(Metric.builder().type(null).build())).build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Missing imp[id=200].metric[0].type");
    }

    @Test
    public void validateImpShouldReturnValidationMessageWhenMetricValueIsNotValid() {
        // given
        final Imp givenImp = validImpBuilder()
                .metric(singletonList(Metric.builder().type("viewability").value(2.0f).build())).build();

        // when & then
        assertThatThrownBy(() -> target.validateImp(givenImp))
                .isInstanceOf(ValidationException.class)
                .hasMessage("imp[id=200].metric[0].value must be in the range [0.0, 1.0]");
    }

    private static List<Imp> givenImps(UnaryOperator<Native.NativeBuilder> nativeCustomizer) {
        return singletonList(validImpBuilder().xNative(nativeCustomizer.apply(Native.builder()).build()).build());
    }

    private static List<Imp> givenNativeImps(UnaryOperator<Request.RequestBuilder> nativeRequestCustomizer)
            throws JsonProcessingException {

        return singletonList(validImpBuilder()
                .xNative(Native.builder()
                        .request(mapper.writeValueAsString(nativeRequestCustomizer.apply(
                                Request.builder()).build()))
                        .build())
                .build());
    }

    private static List<Imp> overwriteBannerFormatInFirstImp(List<Imp> imps,
                                                             UnaryOperator<Format.FormatBuilder> formatModifier) {

        final Banner banner = imps.getFirst().getBanner().toBuilder()
                .format(singletonList(formatModifier.apply(Format.builder()).build())).build();

        return singletonList(validImpBuilder().banner(banner).build());
    }

    private static List<Imp> overwritePmpFirstDealInFirstImp(List<Imp> imps,
                                                             UnaryOperator<Deal.DealBuilder> dealModifier) {

        final Pmp pmp = imps.getFirst().getPmp().toBuilder()
                .deals(singletonList(dealModifier.apply(dealModifier.apply(Deal.builder())).build())).build();

        return singletonList(validImpBuilder().pmp(pmp).build());
    }

    private static List<Imp> givenValidImps() {
        return singletonList(validImpBuilder().build());
    }

    private static Imp.ImpBuilder validImpBuilder() {
        return Imp.builder().id("200")
                .video(Video.builder().mimes(singletonList("vmime"))
                        .build())
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(1).build()))
                        .build())
                .pmp(Pmp.builder().deals(singletonList(Deal.builder().id("1").build())).build())
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))));
    }

}
