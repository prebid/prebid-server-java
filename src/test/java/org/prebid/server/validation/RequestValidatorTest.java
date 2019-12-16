package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.App.AppBuilder;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Deal.DealBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Format.FormatBuilder;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Site.SiteBuilder;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class RequestValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderParamValidator bidderParamValidator;

    private RequestValidator requestValidator;

    @Before
    public void setUp() {
        given(bidderParamValidator.validate(any(), any())).willReturn(Collections.emptySet());
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(true);

        requestValidator = new RequestValidator(bidderCatalog, bidderParamValidator);
    }

    @Test
    public void validateShouldReturnOnlyOneErrorAtATime() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("").build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenTmaxIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("1").tmax(-100L).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.tmax must be nonnegative. Got -100");
    }

    @Test
    public void validateShouldNotReturnValidationMessageWhenTmaxIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().tmax(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenCurIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().cur(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "currency was not defined either in request.cur or in configuration field adServerCurrency");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenExtIsInvalid() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("invalid-prebid"));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).asString().startsWith("request.ext is invalid");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNumberOfImpsIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().imp(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp must contain at least one element");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenImpIdNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder().id(null).build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenImpIdEmptyString() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder().id("").build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNoneOfMediaTypeIsPresent() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .video(null)
                        .audio(null)
                        .banner(null)
                        .xNative(null)
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0] must contain at least one of \"banner\", \"video\", \"audio\", or "
                        + "\"native\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenVideoAttributeIsPresentButVideaMimesMissed() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .video(Video.builder().mimes(emptyList())
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].video.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAudioAttributePresentButAudioMimesMissed() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .audio(Audio.builder().mimes(emptyList())
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].audio.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasNullFormatAndNoSizes() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .format(null)
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBannerHasNullFormatAndValidSizes() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .w(300)
                                .h(250)
                                .format(null)
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoSizes() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoHeight() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .w(300)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNoWidth() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(600)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroHeight() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .w(300)
                                .h(0)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasZeroHeight() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .w(300)
                                .h(0)
                                .format(singletonList(Format.builder().build()))
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndZeroWidth() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(600)
                                .w(0)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasZeroWidth() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(600)
                                .w(0)
                                .format(singletonList(Format.builder().build()))
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeWidth() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(600)
                                .w(-300)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasNegativeWidth() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(600)
                                .w(-300)
                                .format(singletonList(Format.builder().build()))
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasEmptyFormatAndNegativeHeight() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(-300)
                                .w(600)
                                .format(emptyList())
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].banner has no sizes. Define \"w\" and \"h\", or include \"format\" elements");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerHasNegativeHeight() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(Imp.builder()
                        .id("11")
                        .banner(Banner.builder()
                                .h(-300)
                                .w(600)
                                .format(singletonList(Format.builder().build()))
                                .build())
                        .ext(mapper.valueToTree(singletonMap("rubicon", 0)))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHWAndRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHeightWeightAndOneOfRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosAndOneOfSizesPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBannerFormatSizesSpecifiedOnly() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBannerFormatRatiosSpecifiedOnly() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatSizesAndRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                Function.identity());

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} (for static size "
                        + "requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(null).w(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(0).w(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(0));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHeightIsNegative() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(-1).w(2));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatWidthIsNegative() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(2).w(-1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"h\" and \"w\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(null).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define"
                        + " a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(0).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNegative() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(-1).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define "
                        + "a valid \"wmin\", \"wratio\", and \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(null).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\","
                        + " and \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(0).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNegative() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(-1).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and "
                        + "\"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(0));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNegative() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(-1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] must define a valid \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPmpDealIdIsNull() {
        // given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPmpDealIdIsEmptyString() {
        // given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(""));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdAndPageIsNull() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id(null)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdIsEmptyStringAndPageIsNull() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("")).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenPageIdIsNullAndSiteIdIsPresent() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page(null)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldEmptyValidationMessagesWhenSitePageIsEmptyString() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page("")).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdAndPageBothEmpty() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("").page("")).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteExtAmpIsNegative() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("id").page("page")
                        .ext(mapper.valueToTree(ExtSite.of(-1, null)))).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site.ext.amp must be either 1, 0, or undefined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteExtAmpIsGreaterThanOne() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("id").page("page")
                        .ext(mapper.valueToTree(ExtSite.of(2, null)))).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site.ext.amp must be either 1, 0, or undefined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder()
                        .id("id")
                        .page("page")
                        .ext(mapper.createObjectNode().put("amp", "value")))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).asString().startsWith("request.site.ext object is not valid: ");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAppExtIsNotValid() {
        // given
        final ObjectNode invalidExt = mapper.createObjectNode();
        invalidExt.put("prebid", "invalid");

        final BidRequest bidRequest = overwriteApp(
                BidRequest.builder()
                        .id("123")
                        .cur(singletonList("USD"))
                        .imp(singletonList(validImpBuilder().build())),
                appBuilder -> App.builder()
                        .id("3").ext(invalidExt))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).asString().startsWith("request.app.ext object is not valid: ");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestAppAndRequestSiteBothMissed() {
        // given
        final BidRequest.BidRequestBuilder bidRequestBuilder = overwriteSite(validBidRequestBuilder(),
                Function.identity());

        final BidRequest bidRequest = overwriteApp(bidRequestBuilder, Function.identity()).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site or request.app must be defined, but not both");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestAppAndRequestSiteBothPresent() {
        // given
        final BidRequest.BidRequestBuilder bidRequestBuilder = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page("2"));

        final BidRequest bidRequest = overwriteApp(bidRequestBuilder, appBuilder -> App.builder().id("3")).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site or request.app must be defined, but not both");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(null, null))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercIsLessThanZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(-1, null))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercGreaterThanHundred() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(101, null))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(50, null))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercIsLessThanZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(50, -1))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercGreaterThanHundred() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(mapper.valueToTree(ExtDevice.of(ExtDevicePrebid.of(ExtDeviceInt.of(50, 101))))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBidRequestIsOk() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNoImpExtBiddersPresent() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder().ext(null).build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].ext must contain at least one bidder");
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenImpExtBidderIsUnknown() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(false);

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].ext contains unknown bidder: rubicon");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenOnlyPrebidImpExtExist() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .ext(mapper.valueToTree(singletonMap("prebid", "test"))).build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBidderExtIsInvalid() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();
        given(bidderParamValidator.validate(any(), any()))
                .willReturn(new LinkedHashSet<>(asList("errorMessage1", "errorMessage2")));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].ext.rubicon failed validation.\nerrorMessage1\nerrorMessage2");
    }

    @Test
    public void validateShouldNotReturnValidationMessageIfUserExtIsEmptyJsonObject() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder().build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnErrorMessageWhenRegsExtIsEmptyJsonObject() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(null, null))))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsNoValues() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(emptyMap()))
                                .digitrust(ExtUserDigiTrust.of(null, null, 0))
                                .build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.prebid requires a \"buyeruids\" property with at least one ID defined."
                        + " If none exist, then request.user.ext.prebid should not be defined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenCantParseTargetingPriceGranularity() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(new TextNode("pricegranularity"), null, null,
                                null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error while parsing request.ext.prebid.targeting.pricegranularity");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreEmptyList() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(2, emptyList())),
                                null, null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: empty granularity definition supplied");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity
                                .of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0))))), null, null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(-1))))),
                                null, null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrecisionIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(-1,
                                singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))))),
                                null, null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: precision must be non-negative");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMediaTypePriceGranularityTypesAreAllNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(1,
                                singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))))),
                                ExtMediaTypePriceGranularity.of(null, null, null),
                                null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Media type price granularity error: must have at least one media type present");
    }

    @Test
    public void validateShouldReturnValidationMessageWithCorrectMediaType() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(1,
                                singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))))),
                                ExtMediaTypePriceGranularity.of(mapper.valueToTree(ExtPriceGranularity.of(-1,
                                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                BigDecimal.valueOf(1))))), null, null),
                                null, null, null))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Banner price granularity error: precision must be non-negative");
    }

    @Test
    public void validateShouldReturnValidationMessageForInvalidTargeting() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(1,
                                singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))))),
                                null, null, false, false))
                        .build())))
                .build();
        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("ext.prebid.targeting: At least one of includewinners or includebidderkeys"
                        + " must be enabled to enable targeting support");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreNotOrderedByMaxValue() {
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(2,
                                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                                        ExtGranularityRange.of(BigDecimal.valueOf(2), BigDecimal.valueOf(0.05))))),
                                null, null, null, null))
                        .build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: range list must be ordered with increasing \"max\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreNotOrderedByMaxValueInTheMiddleOfRangeList() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(2,
                                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.05)),
                                        ExtGranularityRange.of(BigDecimal.valueOf(8), BigDecimal.valueOf(0.05))))),
                                null, null, null, null))
                        .build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: range list must be ordered with increasing \"max\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsNegativeInNotLeadingElement() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(mapper.valueToTree(ExtPriceGranularity.of(2,
                                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(-0.05))))),
                                null, null, null, null))
                        .build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsUnknownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("unknown-bidder", "42")))
                                .digitrust(ExtUserDigiTrust.of(null, null, 0)).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.unknown-bidder is neither a known bidder name "
                        + "nor an alias in request.ext.prebid.aliases");
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidderAlias() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("unknown-bidder", "rubicon")).build())))
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("unknown-bidder", "42")))
                                .digitrust(ExtUserDigiTrust.of(null, null, 0)).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("rubicon", "42")))
                                .digitrust(ExtUserDigiTrust.of(null, null, 0)).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenDigiTrustPrefNotEqualZero() {
        // given;
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .digitrust(ExtUserDigiTrust.of(null, null, 1)).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user contains a digitrust object that is not valid");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenUserExtFailedToBeParsed() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.put("digitrust", "invalid");
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder().ext(ext).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).asString().contains("request.user.ext object is not valid:");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidsIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(emptyList()).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids must contain at least one element or be undefined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidHasEmptySource() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of(null, null, null, null))).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids[0].source missing required field: \"source\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidHasNoIdOrUids() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("source", null, null, null))).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids[0] must contain either \"id\" or \"uids\" field");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidUidsIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("source", null, emptyList(), null))).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids[0].uids must contain at least one element or be undefined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidUidIdIsMissing() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("source", null,
                                        singletonList(ExtUserEidUid.of(null, null)), null))).build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids[0].uids[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidSourceIsNotUnique() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(asList(
                                        ExtUserEid.of("source", null,
                                                singletonList(ExtUserEidUid.of("id1", null)), null),
                                        ExtUserEid.of("source", null,
                                                singletonList(ExtUserEidUid.of("id2", null)), null)))
                                .build()))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.eids must contain unique sources");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasNameEqualsToBidderItPointsOn() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("rubicon", "rubicon")).build()));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.rubicon defines a no-op alias."
                        + " Choose a different alias, or remove this entry");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasPointOnNotValidBidderName() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "fake")).build()));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.alias refers to unknown bidder: fake");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenAliasesWasUsed() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "rubicon")).build()));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorsWhenGdprIsNotOneOrZero() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtRegs.of(2, null));
        final BidRequest bidRequest = validBidRequestBuilder().regs(Regs.of(null, ext)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.regs.ext.gdpr must be either 0 or 1");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorsWhenRegsExtIsNotValidJson() {
        // given
        final ObjectNode ext = mapper.createObjectNode().put("gdpr", "String");
        final BidRequest bidRequest = validBidRequestBuilder().regs(Regs.of(null, ext)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).asString().contains("request.regs.ext is invalid:");
    }

    @Test
    public void validateShouldThrowExceptionWhenNativeRequestEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(Function.identity());

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp.[0].ext.native contains empty request value");
    }

    @Test
    public void validateShouldThrowExceptionWhenNativeRequestMalformed() {
        // given
        final BidRequest bidRequest = givenBidRequest(nativeCustomizer -> nativeCustomizer.request("broken-request"));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error while parsing request.imp.[0].ext.native.request");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenContextTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(100));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.context is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenContextSubTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(100));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.contextsubtype is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenContextSubTypeAndContextTypeOutOfPossibleContentValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(11));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.context is 2, but contextsubtype is 11. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenContextSubTypeAndContextTypeOutOfPossibleSocialValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(3).contextsubtype(21));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.context is 3, but contextsubtype is 21. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenContextSubTypeAndContextTypeOutOfPossibleProductValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(2).contextsubtype(31));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.context is 2, but contextsubtype is 31. "
                        + "This is an invalid combination. See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=39");
    }

    @Test
    public void validateShouldReturnValidationResultWithEmptyErrorWhenContextSubTypeAndContextTypeValid()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithEmptyErrorWhenContextIsNull()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(null).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithEmptyErrorWhenSubTypeContextIsNull()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(null).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenEventTrackersOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(5).build())).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.eventtrackers[0].event is invalid. See section 7.6: "
                        + "https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenEventTrackerEmptyMethods()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).build())).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.eventtrackers[0].method is required. "
                        + "See section 7.7: https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenEventTrackerInvalidMethod()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).methods(singletonList(3)).build())).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.eventtrackers[0].methods[0] is invalid. "
                        + "See section 7.7: https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=43");
    }

    @Test
    public void validateShouldReturnValidationResultWithEmptyErrorWhenValidEventTracker()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(1).contextsubtype(12).eventtrackers(singletonList(EventTracker.builder()
                        .event(1).methods(singletonList(2)).build())).assets(singletonList(Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenPlacementTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.plcmttype(100));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.plcmttype is invalid. "
                        + "See https://iabtechlab.com/wp-content/uploads/2016/07/"
                        + "OpenRTB-Native-Ads-Specification-Final-1.2.pdf#page=40");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenAssetsContainsZeroElements()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(emptyList()));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets must be an array containing at least one object");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenElementInAssetsHasWhichIsNotUnique()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(asList(
                        Asset.builder().id(1).build(),
                        // this should get ID set on second iteration (i = 1) and result in conflict with previous id
                        Asset.builder().build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[1].id is already being used by another asset. "
                        + "Each asset ID must be unique.");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndImage()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .img(ImageObject.builder().build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndVideo()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .video(VideoObject.builder().build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }


    @Test
    public void validateShouldReturnValidationResultWithErrorWhenIndividualAssetHasTitleAndData()
            throws JsonProcessingException {

        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().build())
                        .data(DataObject.builder().build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenIndividualAssetHasImageAndVideo()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder().build())
                        .video(VideoObject.builder().build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].native.request.assets[0] must define at most one of {title, img, video, data}");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenIndividualAssetHasImageAndData()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder().build())
                        .data(DataObject.builder().build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].native.request.assets[0] must define at most one of {title, img, video, data}");
    }


    @Test
    public void validateShouldReturnValidationResultWithErrorWhenHasZeroTitleLen() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().len(0).build()).build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].title.len must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenHasNullTitleLen() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .title(TitleObject.builder().len(null).build()).build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].title.len must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenDataTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .data(DataObject.builder().type(100).build()).build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].native.request.assets[0].data.type must in the range [1, 12]. Got 100");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMimes()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder().mimes(emptyList()).build()).build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].native.request.assets[0].video.mimes must be an array with at least one"
                                + " MIME type");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMinDuration()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(null)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.minduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasMinDurationLessThanOne()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(0)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.minduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyMaxDuration()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(null)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasMaxDurationLessThanOne()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoHasEmptyProtocols()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .protocols(emptyList())
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenNativeVideoProtocolsOutOfPossibleValues()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(0)
                                .protocols(singletonList(20))
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenNativeVideoIsValid()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .video(VideoObject.builder()
                                .mimes(singletonList("mime"))
                                .minduration(2)
                                .maxduration(2)
                                .protocols(singletonList(0))
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.imp[0].native.request.assets[0].video.protocols[0] must be in the range [1, 10]. Got 0");
    }

    @Test
    public void validateShouldUpdateNativeRequestAssetsIds() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(asList(Asset.builder().build(), Asset.builder().build())));

        // when
        requestValidator.validate(bidRequest);

        assertThat(bidRequest.getImp()).hasSize(1)
                .extracting(Imp::getXNative).doesNotContainNull()
                .extracting(Native::getRequest).doesNotContainNull()
                .extracting(req -> mapper.readValue(req, Request.class))
                .flatExtracting(Request::getAssets)
                .flatExtracting(Asset::getId)
                .containsOnly(0, 1);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMetricTypeNullOrEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .metric(singletonList(Metric.builder().type(null).build())).build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).isEqualTo("Missing request.imp[0].metric[0].type");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMetricValueIsNotValid() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .metric(singletonList(Metric.builder().type("viewability").value(2).build())).build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .element(0).isEqualTo("request.imp[0].metric[0].value must be in the range [0.0, 1.0]");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAdjustmentFactorNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(singletonMap("rubicon", BigDecimal.valueOf(-1.1))).build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.ext.prebid.bidadjustmentfactors.rubicon must be a positive number. Got -1.100000");
    }


    @Test
    public void validateShouldReturnValidationMessageWhenBidderUnknown() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(singletonMap("unknownBidder", BigDecimal.valueOf(1.1F)))
                                .build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.bidadjustmentfactors.unknownBidder is not a known bidder or alias");
    }

    @Test
    public void validateShouldEmptyValidationMessagesWhenBidderIsKnownAndAdjustmentIsValid() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(singletonMap("rubicon", BigDecimal.valueOf(1.1))).build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldEmptyValidationMessagesWhenBidderIsKnownAliasForCoreBidderAndAdjustmentIsValid() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder()
                                .aliases(singletonMap("rubicon_alias", "rubicon"))
                                .bidadjustmentfactors(singletonMap("rubicon_alias", BigDecimal.valueOf(1.1)))
                                .build())))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestHaveDuplicatedImpIds() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(asList(Imp.builder()
                                .id("11")
                                .build(),
                        Imp.builder()
                                .id("11")
                                .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].id and request.imp[1].id are both \"11\". Imp IDs must be unique.");
    }

    private BidRequest givenBidRequest(
            Function<Native.NativeBuilder, Native.NativeBuilder> nativeCustomizer) {
        return validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(nativeCustomizer.apply(Native.builder()).build()).build())).build();
    }

    private BidRequest givenBidRequestWithNativeRequest(
            Function<Request.RequestBuilder, Request.RequestBuilder> nativeRequestCustomizer)
            throws JsonProcessingException {
        return validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(Native.builder()
                                .request(mapper.writeValueAsString(nativeRequestCustomizer.apply(
                                        Request.builder()).build()))
                                .build())
                        .build()))
                .build();
    }

    private static BidRequest.BidRequestBuilder validBidRequestBuilder() {
        return BidRequest.builder().id("1").tmax(300L)
                .cur(singletonList("USD"))
                .imp(singletonList(validImpBuilder().build()))
                .site(Site.builder().id("1").page("2").build());
    }

    private static Imp.ImpBuilder validImpBuilder() {
        return Imp.builder().id("200")
                .video(Video.builder().mimes(singletonList("vmime"))
                        .build())
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(1).build()))
                        .build())
                .pmp(Pmp.builder().deals(singletonList(Deal.builder().id("1").build())).build())
                .ext(mapper.valueToTree(singletonMap("rubicon", 0)));
    }

    private static BidRequest overwriteBannerFormatInFirstImp(
            BidRequest bidRequest, Function<FormatBuilder, FormatBuilder> formatModifier) {
        final Banner banner = bidRequest.getImp().get(0).getBanner().toBuilder()
                .format(singletonList(formatModifier.apply(Format.builder()).build())).build();

        return bidRequest.toBuilder().imp(singletonList(validImpBuilder().banner(banner).build())).build();
    }

    private static BidRequest overwritePmpFirstDealInFirstImp(
            BidRequest bidRequest, Function<DealBuilder, DealBuilder> dealModifier) {
        final Pmp pmp = bidRequest.getImp().get(0).getPmp().toBuilder()
                .deals(singletonList(dealModifier.apply(dealModifier.apply(Deal.builder())).build())).build();

        return bidRequest.toBuilder().imp(singletonList(validImpBuilder().pmp(pmp).build())).build();
    }

    private static BidRequest.BidRequestBuilder overwriteSite(
            BidRequest.BidRequestBuilder builder, Function<SiteBuilder, SiteBuilder> siteModifier) {
        return builder.site(siteModifier.apply(Site.builder()).build());
    }

    private static BidRequest.BidRequestBuilder overwriteApp(
            BidRequest.BidRequestBuilder builder, Function<AppBuilder, AppBuilder> appModifier) {
        return builder.app(appModifier.apply(App.builder()).build());
    }
}
