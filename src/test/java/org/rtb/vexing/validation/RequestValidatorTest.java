package org.rtb.vexing.validation;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestValidatorTest {

    private RequestValidator requestValidator;

    @Before
    public void setUp() {
        requestValidator = new RequestValidator();
    }

    @Test
    public void shouldValidateOnlyOneErrorAtATime() {
        //given
        final BidRequest bidRequest = BidRequest.builder().build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result).isNotNull();
        assertThat(result.errors).hasSize(1);
    }

    @Test
    public void shouldValidateEmptyStringRequestIdValue() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().id("").build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request missing required field: \"id\"");
    }

    @Test
    public void shouldValidateNullRequestIdValue() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().id(null).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request missing required field: \"id\"");
    }

    @Test
    public void shouldValidateNegativeTmax() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().id("1").tmax(-100L).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.tmax must be nonnegative. Got -100");
    }

    @Test
    public void shouldNotValidateNegativeTMaxIfAttributeMissed() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().tmax(null).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then

        assertThat(result.errors).isEmpty();
    }

    @Test
    public void shouldValidateNumberOfImps() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().imp(null).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp must contain at least one element.");
    }

    @Test
    public void shouldValidateImpIdNull() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder().id(null).build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void shouldValidateImpIdEmptyString() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder().id("").build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0] missing required field: \"id\"");
    }

    @Test
    public void shouldValidateMetricsNotSupported() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .metric(singletonList(Metric.builder().type("none")
                                .build()))
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].metric is not yet supported by prebid-server. " +
                "Support may be added in the future.");
    }

    @Test
    public void shouldValidateIfAnyMediaPresentInRequest() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .video(null)
                        .audio(null)
                        .banner(null)
                        .xNative(null)
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0] must contain at least one of \"banner\", " +
                "\"video\", \"audio\", or \"native\"");
    }

    @Test
    public void shouldValidateVideoMimesIfVideoAttrPresentInRequest() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .video(Video.builder().mimes(emptyList())
                                .build())
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].video.mimes must contain at least one " +
                "supported MIME type");
    }

    @Test
    public void shouldValidateAudioMimesIfAudioAttrPresentInRequest() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .audio(Audio.builder().mimes(emptyList())
                                .build())
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].audio.mimes must contain at least one " +
                "supported MIME type");
    }

    @Test
    public void shouldValidateNativeRequestAttributeNullValue() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(Native.builder().request(null).build())
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].native.request must be a JSON encoded string" +
                " conforming to the openrtb 1.2 Native spec");
    }

    @Test
    public void shouldValidateNativeRequestAttributeEmpty() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(singletonList(validImpBuilder()
                        .xNative(Native.builder().request("").build())
                        .build()))
                .build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].native.request must be a JSON encoded string" +
                " conforming to the openrtb 1.2 Native spec");
    }

    @Test
    public void shouldValidateBannerFormatWhenBothHWAndRatiosPresent() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).wmin(3).wratio(4).hratio(5));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] should define *either* " +
                "{w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" " +
                "objects in the request.");
    }

    @Test
    public void shouldValidateBannerFormatWhenHeightWeightAndOneOfRatiosPresent() {
        //give
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2).hratio(5));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] should define *either* " +
                "{w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" " +
                "objects in the request.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosAndOneOfSizesPresent() {

        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).wmin(3).wratio(4).hratio(5));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] should define *either* " +
                "{w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" " +
                "objects in the request.");
    }

    @Test
    public void shouldValidateBannerFormatWhenSizesSpecifiedOnly() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(2));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.errors).hasSize(0);
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosSpecifiedOnly() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(3).wratio(4).hratio(5));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.errors).hasSize(0);
    }

    @Test
    public void shouldValidateBannerFormatWhenNeitherSizesNorRatiosPresent() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
               Function.identity());

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] should define *either* {w, h}" +
                " (for static size requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero.");
    }

    @Test
    public void shouldValidateBannerFormatWhenStaticSizesUsedAndHeightIsNull() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(null).w(1));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"h\" " +
                "and \"w\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenStaticSizesUsedAndHeightIsZero() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(0).w(1));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"h\" " +
                "and \"w\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenStaticSizesUsedAndWeightIsNull() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(null));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"h\" " +
                "and \"w\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenStaticSizesUsedAndWeightIsZero() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().h(1).w(0));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"h\" " +
                "and \"w\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndWMinIsNull() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(null).wratio(2).hratio(1));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndWMinIsZero() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(0).wratio(2).hratio(1));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndWRatioIsNull() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(null).hratio(1));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndWRatioIsZero() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(0).hratio(1));


        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndHRatioIsNull() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(null));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidateBannerFormatWhenRatiosUsedAndHRatioIsZero() {
        //given
        final BidRequest bidRequest = overwriteBannerFormatInFirstImp(validBidRequestBuilder().build(),
                formatBuilder -> Format.builder().wmin(1).wratio(5).hratio(0));

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "Request imp[0].banner.format[0] must define non-zero \"wmin\"," +
                " \"wratio\", and \"hratio\" properties.");
    }

    @Test
    public void shouldValidatePmpDealIdIsNotNull() {
        //given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(null));


        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void shouldValidatePmpDealIdIsEmptyString() {
        //given
        final BidRequest bidRequest = overwritePmpFirstDealInFirstImp(validBidRequestBuilder().build(),
                dealBuilder -> Deal.builder().id(""));
        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.imp[0].pmp.deals[0] missing required field: \"id\"");
    }

    @Test
    public void shouldValidateSiteIdAndPageIsNull() {
        //given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id(null)).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.site should include at least one of request.site.id " +
                "or request.site.page.");
    }

    @Test
    public void shouldValidateSiteIdIsEmptyStringAndPageIsNull() {
        //given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("")).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.site should include at least one of request.site.id " +
                "or request.site.page.");
    }

    @Test
    public void shouldValidatePageIdIsNullAndSiteIdIsPresent() {
        //given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page(null)).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void shouldValidateSitePageIsEmptyString() {
        //given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page("")).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void shouldValidateSiteEmptyIdAndPageEmpty() {
        //given
        final BidRequest bidRequest = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("").page("")).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.site should include at least one of request.site.id " +
                "or request.site.page.");
    }
    @Test
    public void shouldValidateRequestAppAndRequestSiteBothMissed() {
        //given
        final BidRequest.BidRequestBuilder bidRequestBuilder = overwriteSite(validBidRequestBuilder(),
                Function.identity());

        final BidRequest bidRequest = overwriteApp(bidRequestBuilder, Function.identity()).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.site or request.app must be defined, but not both.");
    }

    @Test
    public void shouldValidateRequestAppAndRequestSiteBothPresent() {
        //given
        final BidRequest.BidRequestBuilder bidRequestBuilder = overwriteSite(validBidRequestBuilder(),
                siteBuilder -> Site.builder().id("1").page("2"));

        final BidRequest bidRequest = overwriteApp(bidRequestBuilder, appBuilder -> App.builder().id("3")).build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertValidationResult(result, "request.site or request.app must be defined, but not both.");
    }

    @Test
    public void shouldValidateBidRequestSuccess() {
        //given
        final BidRequest bidRequest = validBidRequestBuilder().build();

        //when
        final ValidationResult result = requestValidator.validate(bidRequest);

        //then
        assertThat(result.errors).isEmpty();
    }

    private static void assertValidationResult(ValidationResult result, String msg) {
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0)).isEqualTo(msg);
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
                .pmp(Pmp.builder().deals(singletonList(Deal.builder().id("1").build())).build());
    }

    private static BidRequest overwriteBannerFormatInFirstImp(BidRequest bidRequest,
                                                              Function<Format.FormatBuilder,
                                                                      Format.FormatBuilder> formatModifier) {
        bidRequest.getImp().get(0).getBanner()
                .setFormat(singletonList(formatModifier.apply(Format.builder()).build()));
        return bidRequest;
    }

    private static BidRequest overwritePmpFirstDealInFirstImp(BidRequest bidRequest,
                                                              Function<Deal.DealBuilder,
                                                                      Deal.DealBuilder> dealModifier) {
        final Pmp pmp =  bidRequest.getImp().get(0).getPmp().toBuilder()
                .deals((singletonList(dealModifier.apply(dealModifier.apply(Deal.builder())).build()))).build();

        return bidRequest.toBuilder().imp(singletonList(validImpBuilder().pmp(pmp).build())).build();
    }

    private static BidRequest.BidRequestBuilder overwriteSite(BidRequest.BidRequestBuilder builder,
                                                              Function<Site.SiteBuilder,
                                                                      Site.SiteBuilder> siteModifier) {
        return builder.site(siteModifier.apply(Site.builder()).build());
    }

    private static BidRequest.BidRequestBuilder overwriteApp(BidRequest.BidRequestBuilder builder,
                                                             Function<App.AppBuilder,
                                                                     App.AppBuilder> appModifier) {
        return builder.app(appModifier.apply(App.builder()).build());
    }

}