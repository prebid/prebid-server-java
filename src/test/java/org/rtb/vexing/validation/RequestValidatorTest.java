package org.rtb.vexing.validation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.App.AppBuilder;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Deal.DealBuilder;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Format.FormatBuilder;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Site.SiteBuilder;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.auction.BidderRequesterCatalog;
import org.rtb.vexing.model.openrtb.ext.request.ExtUserDigiTrust;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.function.Function;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class RequestValidatorTest extends VertxTest {

    public static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderRequesterCatalog bidderRequesterCatalog;
    @Mock
    private BidderParamValidator bidderParamValidator;

    private RequestValidator requestValidator;

    @Before
    public void setUp() {
        given(bidderParamValidator.validate(any(), any())).willReturn(Collections.emptySet());
        given(bidderRequesterCatalog.isValidName(eq(RUBICON))).willReturn(Boolean.TRUE);

        requestValidator = new RequestValidator(bidderRequesterCatalog, bidderParamValidator);
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
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenTmaxIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("1").tmax(-100L).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("request.tmax must be nonnegative. Got -100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenTmaxIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().tmax(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenExtIsInvalid() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", new TextNode("invalid-prebid"));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1).element(0).asString().startsWith("request.ext is invalid");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNumberOfImpsIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().imp(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp must contain at least one element.");
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
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("request.imp[0] missing required field: \"id\"");
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
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMetricTypeIsSpecified() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .metric(singletonList(Metric.builder().type("none")
                                .build()))
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].metric is not yet supported by prebid-server. " +
                        "Support may be added in the future.");
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0] must contain at least one of \"banner\", \"video\", \"audio\", or " +
                        "\"native\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenVideAttributeIsPresentButVideaMimesMissed() {
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].video.mimes must contain at least one supported MIME type");
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].audio.mimes must contain at least one supported MIME type");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNativeRequestAttributeNullValue() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(Native.builder().request(null).build())
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].native.request must be a JSON encoded string conforming to the openrtb 1.2" +
                        " Native spec");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNativeRequestAttributeEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(Native.builder().request("").build())
                        .build()))
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].native.request must be a JSON encoded string conforming to the openrtb 1.2" +
                        " Native spec");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHWAndRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, " +
                        "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHeightWeightAndOneOfRatiosPresent() {
        //give
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, " +
                        "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosAndOneOfSizesPresent() {

        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, " +
                        "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatSizesSpecifiedOnly() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosSpecifiedOnly() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatSizesAndRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                Function.identity());

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] should define *either* {w, h} (for static size " +
                        "requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(null).w(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndHeightIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(0).w(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatStaticSizesUsedAndWeightIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(0));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(null).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define " +
                        "non-zero \"wmin\", \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(0).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define " +
                        "non-zero \"wmin\", \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(null).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and \"hratio\"" +
                        " properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWRatioIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(0).hratio(1));


        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and \"hratio\"" +
                        " properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsNull() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and \"hratio\"" +
                        " properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndHRatioIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(0));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and \"hratio\"" +
                        " properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPmpDealIdIsNull() {
        // given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(null));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPmpDealIdIsEmptyString() {
        // given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(""));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdAndPageIsNull() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id(null)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.site should include at least one of request.site.id or request.site.page.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdIsEmptyStringAndPageIsNull() {
        // given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("")).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.site should include at least one of request.site.id or request.site.page.");
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
    public void validateShouldReturnValidationMessageWhenSitePageIsEmptyString() {
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.site should include at least one of request.site.id or request.site.page.");
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.site or request.app must be defined, but not both.");
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.site or request.app must be defined, but not both.");
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
                .imp(singletonList(validImpBuilder()
                        .ext(null).build())).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].ext must contain at least one bidder");
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenImpExtBidderIsUnknown() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();
        given(bidderRequesterCatalog.isValidName(eq(RUBICON))).willReturn(Boolean.FALSE);

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].ext contains unknown bidder: rubicon");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenOnlyPrebidImpExtExist() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .ext(mapper.valueToTree(singletonMap("prebid", "test"))).build())).build();

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
                .willReturn(new LinkedHashSet<>(Arrays.asList("errorMessage1", "errorMessage2")));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].ext.rubicon failed validation.\nerrorMessage1\nerrorMessage2");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenDigiTrustOmittedForUserExt() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder().ext(ext).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.user contains a digitrust object that is not valid.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenDigiTrustPrefEqualZero() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtUserDigiTrust.of(null, null, 0));
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder().ext(ext).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.user contains a digitrust object that is not valid.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenUserExtFailedToBeParsed() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.put("digitrust", "invalid");
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder().ext(ext).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.user.ext object is not valid.");
    }

    private static BidRequest.BidRequestBuilder validBidRequestBuilder() {
        return BidRequest.builder().id("1").tmax(300L)
                .imp(singletonList(validImpBuilder().build()))
                .site(Site.builder().id("1").page("2").build());
    }

    private static Imp.ImpBuilder validImpBuilder() {
        return Imp.builder().id("200")
                .video(Video.builder().mimes(singletonList("vmime"))
                        .build())
                .xNative(Native.builder().request("{\"param\" : \"val\"}").build())
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
                .deals((singletonList(dealModifier.apply(dealModifier.apply(Deal.builder())).build()))).build();

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
