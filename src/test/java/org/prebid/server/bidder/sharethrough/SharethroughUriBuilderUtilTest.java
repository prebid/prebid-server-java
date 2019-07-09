package org.prebid.server.bidder.sharethrough;

import org.junit.Test;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharethroughUriBuilderUtilTest {

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
    public void buildSharethroughUrlParametersShouldThrowIllegalArgumentExceptionWhenWidthtIsNotNumberOrNotPresent() {
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
        assertFalse(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull).getIframe());
        assertFalse(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriEmpty).getIframe());
    }

    @Test
    public void buildSharethroughUrlParametersShouldConsentRequiredBeFalseWhenConsentRequiredIsNotBoolean() {
        // given
        final String uriNull = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&consentRequired=null";
        final String uriEmpty = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&consentRequired=";

        // when and then
        assertFalse(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriNull).getConsentRequired());
        assertFalse(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriEmpty).getConsentRequired());
    }

    @Test
    public void buildSharethroughUrlParametersShouldReturnOptionalParametersWithEmptyStringWhenUriNotContainsOptionalParameters() {
        // given
        final String uriWithoutOptionalParameters = "http://uri.com?height=30&width=30";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriWithoutOptionalParameters))
                .satisfies(strUriParameters -> {
                    assertEquals("", strUriParameters.getBidID());
                    assertEquals("", strUriParameters.getPkey());
                    assertEquals("", strUriParameters.getConsentString());
                });
    }

    @Test
    public void buildSharethroughUrlParametersShouldPopulateWithParametersWhenUriContainsParameters() {
        // given
        final String uriWithoutOptionalParameters = "http://uri.com?placement_key=pkey&bidId=bidid&height=30&width=30&consent_required=true&stayInIframe=true&consent_string=123&";

        // when and then
        assertThat(SharethroughUriBuilderUtil.buildSharethroughUrlParameters(uriWithoutOptionalParameters))
                .satisfies(strUriParameters -> {
                    assertThat(strUriParameters.getHeight()).isEqualTo(30);
                    assertThat(strUriParameters.getWidth()).isEqualTo(30);
                    assertEquals("bidid", strUriParameters.getBidID());
                    assertEquals("pkey", strUriParameters.getPkey());
                    assertEquals("123", strUriParameters.getConsentString());
                    assertTrue(strUriParameters.getConsentRequired());
                    assertTrue(strUriParameters.getIframe());
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
                .bidID("2bidId2")
                .pkey("3pkey3")
                .build();

        final String baseUri = "http://uri.com";
        final String supplyId = "suId";
        final String strVersion = "version";

        // when
        final String expected = "http://uri.com?placement_key=3pkey3&bidId=2bidId2&consent_required=true&consent_string=1consentString1&instant_play_capable=true&stayInIframe=true&height=100&width=200&supplyId=suId&strVersion=version";
        final String result = SharethroughUriBuilderUtil.buildSharethroughUrl(baseUri, supplyId, strVersion, strUriParameters);

        // then
        assertEquals(expected, result);
    }
}

