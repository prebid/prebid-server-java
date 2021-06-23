package org.prebid.server.bidder.sharethrough;

import org.junit.Test;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class SharethroughUriBuilderUtilTest {

    @Test
    public void buildSharethroughUrlParametersShouldThrowIllegalArgumentExceptionWhenEndpointUrlComposingFails() {
        // given
        final String uri = "http://invalid domain.com";

        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uri));
    }

    @Test
    public void buildSharethroughUrlParametersShouldThrowIllegalArgumentExceptionWhenHeightIsNotNumberOrNotPresent() {
        // given
        final String uriNull = "http://uri.com?placement_key=pkey&bidId=bidid&height=null&width=30";
        final String uriNotNumber = "http://uri.com?placement_key=pkey&bidId=bidid&height=notnumber&width=30";
        final String uriNotPresent = "http://uri.com?placement_key=pkey&bidId=bidid&width=30";

        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNotNumber));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNotPresent));
    }

    @Test
    public void buildSharethroughUrlParametersShouldThrowIllegalArgumentExceptionWhenWidthIsNotNumberOrNotPresent() {
        // given
        final String uriNull = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=null";
        final String uriNotNumber = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=notNumber";
        final String uriNotPresent = "http://uri.com?placement_key=pkey&bidId=bidid&height=30";

        // when and then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNotNumber));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNotPresent));
    }

    @Test
    public void buildSharethroughUrlParametersShouldStayInFrameBeFalseWhenStayInFrameIsNotBoolean() {
        // given
        final String uriNull = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&stayInIframe=null";
        final String uriEmpty = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&stayInIframe=";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull).getIframe()).isFalse();
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriEmpty).getIframe()).isFalse();
    }

    @Test
    public void buildSharethroughUrlParametersShouldConsentRequiredBeFalseWhenConsentRequiredIsNotBoolean() {
        // given
        final String uriNull = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&consentRequired=null";
        final String uriEmpty = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&consentRequired=";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull).getConsentRequired()).isFalse();
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriEmpty).getConsentRequired()).isFalse();
    }

    @Test
    public void buildSharethroughUrlParametersShouldReturnOptionalParametersWithEmptyStringWhenUriHasNoOptParameters() {
        // given
        final String uriWithoutOptionalParameters = "http://uri.com?height=30&width=30";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriWithoutOptionalParameters))
                .satisfies(strUriParameters -> {
                    assertThat(strUriParameters.getBidID()).isEmpty();
                    assertThat(strUriParameters.getPkey()).isEmpty();
                    assertThat(strUriParameters.getConsentString()).isEmpty();
                });
    }

    @Test
    public void buildSharethroughUrlParametersShouldPopulateWithParametersWhenUriContainsParameters() {
        // given
        final String uriWithoutOptionalParameters = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30"
                + "&consent_required=true&stayInIframe=true&consent_string=123&";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriWithoutOptionalParameters))
                .satisfies(strUriParameters -> {
                    assertThat(strUriParameters.getHeight()).isEqualTo(30);
                    assertThat(strUriParameters.getWidth()).isEqualTo(30);
                    assertThat(strUriParameters.getBidID()).isEqualTo("bidid");
                    assertThat(strUriParameters.getPkey()).isEqualTo("pkey");
                    assertThat(strUriParameters.getConsentString()).isEqualTo("123");
                    assertThat(strUriParameters.getConsentRequired()).isTrue();
                    assertThat(strUriParameters.getIframe()).isTrue();
                });
    }

    @Test
    public void buildSharethroughUrlShouldReturnUriWithParametersFromStrUriParameters() {
        // given
        final StrUriParameters strUriParameters = StrUriParameters.builder()
                .height(100)
                .width(200)
                .iframe(true)
                .consentRequired(true)
                .instantPlayCapable(true)
                .consentString("1consentString1")
                .usPrivacySignal("ccpa")
                .bidID("2bidId2")
                .pkey("3pkey3")
                .theTradeDeskUserId("ttd123")
                .sharethroughUserId("uuid")
                .build();

        final String baseUri = "http://uri.com";
        final String supplyId = "suId";
        final String strVersion = "version";

        // when
        final String expected = "http://uri.com?placement_key=3pkey3&bidId=2bidId2&consent_required=true&"
                + "consent_string=1consentString1&us_privacy=ccpa&instant_play_capable=true&stayInIframe=true&"
                + "height=100&width=200&adRequestAt=testDate&supplyId=suId&strVersion=version&ttduid=ttd123&"
                + "stxuid=uuid";
        final String result = SharethroughUriBuilderUtil.buildSharethroughUrl(baseUri, supplyId, strVersion, "testDate",
                strUriParameters);

        // then
        assertThat(result).isEqualTo(expected);
    }
}

