package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class USNatTransmitGeoTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USNatGppReader gppReader;

    @Test
    public void proceedShouldDisallowIfMspaServiceProviderModeEquals1() {
        // given
        given(gppReader.getMspaServiceProviderMode()).willReturn(1);
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfGpcEqualsTrue() {
        // given
        given(gppReader.getGpc()).willReturn(true);
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSensitiveDataLimitUseNoticeEquals2() {
        // given
        given(gppReader.getSensitiveDataLimitUseNotice()).willReturn(2);
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSensitiveDataProcessingOptOutNoticeEquals2() {
        // given
        given(gppReader.getSensitiveDataProcessingOptOutNotice()).willReturn(2);
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSensitiveDataProcessingOptOutNoticeIs0AndIfSensitiveDataProcessing8Is2() {
        // given
        given(gppReader.getSensitiveDataProcessingOptOutNotice()).willReturn(0);
        given(gppReader.getSensitiveDataProcessing()).willReturn(asList(0, 0, 0, 0, 0, 0, 0, 2));

        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSensitiveDataLimitUseNoticeEquals0AndIfSensitiveDataProcessing8Equals2() {
        // given
        given(gppReader.getSensitiveDataLimitUseNotice()).willReturn(0);
        given(gppReader.getSensitiveDataProcessing()).willReturn(asList(0, 0, 0, 0, 0, 0, 0, 2));

        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSensitiveDataProcessing8Equals1() {
        // given
        given(gppReader.getSensitiveDataProcessing()).willReturn(asList(0, 0, 0, 0, 0, 0, 0, 1));
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents1Equals1() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(singletonList(1));
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents2Equals1() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(asList(1, 1));
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents2Equals2() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(asList(1, 2));
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfPersonalDataConsentsEquals2() {
        // given
        given(gppReader.getPersonalDataConsents()).willReturn(2);
        final PrivacyModule target = new USNatTransmitGeo(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldAllow() {
        // given
        final PrivacyModule target = new USNatTransmitUfpd(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ALLOW);
    }
}
