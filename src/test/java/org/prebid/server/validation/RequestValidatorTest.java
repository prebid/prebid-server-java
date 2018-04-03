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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.validation.model.ValidationResult;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.function.Function;

import static java.util.Arrays.asList;
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
        assertThat(result.getErrors()).hasSize(1).containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenTmaxIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("1").tmax(-100L).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.tmax must be nonnegative. Got -100");
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

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).asString().startsWith("request.ext is invalid");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNumberOfImpsIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().imp(null).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.imp must contain at least one element.");
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
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.imp[0] missing required field: \"id\"");
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
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.imp[0] missing required field: \"id\"");
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
    public void validateShouldReturnValidationMessageWhenBannerFormatHWAndRatiosPresent() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).wmin(3).wratio(4).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                       + "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatHeightWeightAndOneOfRatiosPresent() {
        //give
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).hratio(5));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} *or* {wmin, wratio, "
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
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
                        + "hratio}, but not both. If both are valid, send two \"format\" objects in the request.");
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
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Request imp[0].banner.format[0] should define *either* {w, h} (for static size "
                        + "requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"h\" and \"w\" properties.");
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
                        + " non-zero \"wmin\", \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBannerFormatRatiosUsedAndWMinIsZero() {
        // given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(0).wratio(2).hratio(1));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("Request imp[0].banner.format[0] must define "
                        + "non-zero \"wmin\", \"wratio\", and \"hratio\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\","
                        + " and \"hratio\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and "
                       + "\"hratio\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and"
                        + " \"hratio\" properties.");
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
                .containsOnly("Request imp[0].banner.format[0] must define non-zero \"wmin\", \"wratio\", and"
                       + " \"hratio\" properties.");
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
                .containsOnly("request.site should include at least one of request.site.id or request.site.page.");
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
                .containsOnly("request.site should include at least one of request.site.id or request.site.page.");
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
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page.");
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
                .containsOnly("request.site or request.app must be defined, but not both.");
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
                .containsOnly("request.site or request.app must be defined, but not both.");
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
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.imp[0].ext must contain at least one bidder");
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenImpExtBidderIsUnknown() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(false);

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.imp[0].ext contains unknown bidder: rubicon");
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
                .willReturn(new LinkedHashSet<>(asList("errorMessage1", "errorMessage2")));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].ext.rubicon failed validation.\nerrorMessage1\nerrorMessage2");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidAndDigiTrustOfUserExtMissed() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder()
                .ext(mapper.valueToTree(ExtUser.of(null, null, null))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.user.ext should not be an empty object.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsNoValues() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder()
                .ext(mapper.valueToTree(
                        ExtUser.of(ExtUserPrebid.of(emptyMap()), null, ExtUserDigiTrust.of(null, null, 0)))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.user.ext.prebid requires a "
                + "\"buyeruids\" property with at least one ID defined. If none exist, then request.user.ext.prebid "
                + "should not be defined.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsUnknownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().user(User.builder()
                .ext(mapper.valueToTree(ExtUser.of(
                        ExtUserPrebid.of(singletonMap("unknown-bidder", "42")),
                        null,
                        ExtUserDigiTrust.of(null, null, 0)))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.user.ext.unknown-bidder "
                + "is neither a known bidder name nor an alias in request.ext.prebid.aliases.");
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidderAlias() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                        singletonMap("unknown-bidder", "rubicon"), null, null, null))))
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.of(
                                ExtUserPrebid.of(singletonMap("unknown-bidder", "42")),
                                null,
                                ExtUserDigiTrust.of(null, null, 0)))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.of(
                                ExtUserPrebid.of(singletonMap("rubicon", "42")),
                                null,
                                ExtUserDigiTrust.of(null, null, 0)))).build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenDigiTrustPrefNotEqualZero() {
        // given;
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder().ext(mapper.valueToTree(ExtUser.of(
                        ExtUserPrebid.of(singletonMap("bidder", "uidval")),
                        null,
                        ExtUserDigiTrust.of(null, null, 1))))
                        .build())
                .build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user contains a digitrust object that is not valid.");
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

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).asString()
                .contains("request.user.ext object is not valid:");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasNameEqualsToBidderItPointsOn() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                singletonMap("rubicon", "rubicon"), null, null, null)));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.ext.prebid.aliases.rubicon defines a no-op alias."
                        + " Choose a different alias, or remove this entry.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasPointOnNotValidBidderName() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                singletonMap("alias", "fake"), null, null, null)));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.ext.prebid.aliases.alias refers to unknown bidder: fake");
    }

    @Test
    public void validateShouldReturnValidationResultWithoutErrorMessageWhenAliasesWasUsed() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(
                singletonMap("alias", "rubicon"), null, null, null)));
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorsWhenGdprIsNotOneOrZero() {
        // given
        final ObjectNode ext = mapper.valueToTree(ExtRegs.of(2));
        final BidRequest bidRequest = validBidRequestBuilder().regs(Regs.of(0, ext)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly("request.regs.ext.gdpr must be either 0 or 1.");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorsWhenRegsExtIsNotValidJson() {
        // given
        final ObjectNode ext = mapper.createObjectNode().put("gdpr", "String");
        final BidRequest bidRequest = validBidRequestBuilder().regs(Regs.of(0, ext)).build();

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).element(0).asString().contains("request.regs.ext is invalid:");
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
    public void validateShouldReturnValidationResultWithErrorWhenContentTypeOutOfPossibleValuesRange()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.context(100));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.context must be in the range [1, 3]. Got 100");
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
                .containsOnly("request.imp[0].native.request.plcmttype must be in the range [1, 4]. Got 100");
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
                .containsOnly("request.imp[0].native.request.assets must be an array containing at least one object.");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenElementInAssetsHasIdSet()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder().id(1).build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].native.request.assets[0].id must not be defined. Prebid Server will"
                        + " set this automatically, using the index of the asset in the array as the ID.");
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
                .containsOnly("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");

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
                .containsOnly("request.imp[0].native.request.assets[0] must define at most one of"
                        + " {title, img, video, data}");

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
    public void validateShouldReturnValidationResultWithErrorWhenImageWidthsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest0 = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .w(null).wmin(null)
                                .h(1).hmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result0 = requestValidator.validate(bidRequest0);

        // then
        assertThat(result0.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"w\" or \"wmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageWidthsZero() throws JsonProcessingException {
        // given
        final BidRequest bidRequest1 = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .w(0).wmin(0)
                                .h(1).hmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result1 = requestValidator.validate(bidRequest1);

        // then
        assertThat(result1.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"w\" or \"wmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageWidthNullAndWidthMinZero()
            throws JsonProcessingException {

        // given
        final BidRequest bidRequest2 = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .w(null).wmin(0)
                                .h(1).hmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result2 = requestValidator.validate(bidRequest2);

        // then
        assertThat(result2.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"w\" or \"wmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageWidthZeroAndWidthMinNull()
            throws JsonProcessingException {

        // given
        final BidRequest bidRequest2 = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .w(0).wmin(null)
                                .h(1).hmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result2 = requestValidator.validate(bidRequest2);

        // then
        assertThat(result2.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"w\" or \"wmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageHeightsNull() throws JsonProcessingException {
        // given
        final BidRequest bidRequest0 = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .h(null).hmin(null)
                                .w(1).wmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result0 = requestValidator.validate(bidRequest0);

        // then
        assertThat(result0.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"h\" or \"hmin\"");

    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageHeightsZero() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .h(0).hmin(0)
                                .w(1).wmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"h\" or \"hmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageHeightZeroAndHeightMinNull()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .h(0).hmin(null)
                                .w(1).wmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"h\" or \"hmin\"");
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorWhenImageHeightNullAndHeightMinZero()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithNativeRequest(nativeReqCustomizer ->
                nativeReqCustomizer.assets(singletonList(Asset.builder()
                        .img(ImageObject.builder()
                                .h(0).hmin(0)
                                .w(1).wmin(1)
                                .build())
                        .build())));

        // when
        final ValidationResult result = requestValidator.validate(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].img must contain at least one of \"h\" or \"hmin\"");
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
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.imp[0].native.request.assets[0].data.type must in the range [1, 12]. Got 100.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.mimes must be an array with at least one"
                        +" MIME type.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.minduration must be a positive integer.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.minduration must be a positive integer.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer.");
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
                .containsOnly("request.imp[0].native.request.assets[0].video.maxduration must be a positive integer.");
    }

    @Test
    public void validateShouldReturnValidationResultWithOutErrorsWhenNativeVideoIsValid()
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
        assertThat(result.getErrors()).hasSize(0);
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
        assertThat(result.getErrors()).hasSize(1).element(0).isEqualTo("Missing request.imp[0].metric[0].type");
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
        assertThat(result.getErrors()).hasSize(1).element(0)
                .isEqualTo("request.imp[0].metric[0].value must be in the range [0.0, 1.0]");
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
