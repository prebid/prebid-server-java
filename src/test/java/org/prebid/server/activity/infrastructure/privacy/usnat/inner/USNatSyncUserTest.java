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

public class USNatSyncUserTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USNatGppReader gppReader;

    @Test
    public void proceedShouldDisallowIfMspaServiceProviderModeEquals1() {
        // given
        given(gppReader.getMspaServiceProviderMode()).willReturn(1);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfGpcEqualsTrue() {
        // given
        given(gppReader.getGpc()).willReturn(true);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSaleOptOutEquals1() {
        // given
        given(gppReader.getSaleOptOut()).willReturn(1);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSaleOptOutNoticeEquals2() {
        // given
        given(gppReader.getSaleOptOutNotice()).willReturn(2);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSaleOptOutNoticeEquals0AndSaleOptOutEquals2() {
        // given
        given(gppReader.getSaleOptOut()).willReturn(2);
        given(gppReader.getSaleOptOutNotice()).willReturn(0);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSharingNoticeEquals2() {
        // given
        given(gppReader.getSharingNotice()).willReturn(2);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSharingOptOutEquals1() {
        // given
        given(gppReader.getSharingOptOut()).willReturn(1);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSharingOptOutNoticeEquals2() {
        // given
        given(gppReader.getSharingOptOutNotice()).willReturn(2);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSharingNoticeEquals0AndSharingOptOutEquals2() {
        // given
        given(gppReader.getSharingOptOut()).willReturn(2);
        given(gppReader.getSharingNotice()).willReturn(0);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfSharingOptOutNoticeEquals0AndSharingOptOutEquals2() {
        // given
        given(gppReader.getSharingOptOut()).willReturn(2);
        given(gppReader.getSharingOptOutNotice()).willReturn(0);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfTargetedAdvertisingOptOutEquals1() {
        // given
        given(gppReader.getTargetedAdvertisingOptOut()).willReturn(1);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfTargetedAdvertisingOptOutNoticeEquals2() {
        // given
        given(gppReader.getTargetedAdvertisingOptOutNotice()).willReturn(2);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfTargetedAdvertisingOptOutNoticeEquals0AndTargetedAdvertisingOptOutEquals2() {
        // given
        given(gppReader.getTargetedAdvertisingOptOut()).willReturn(2);
        given(gppReader.getTargetedAdvertisingOptOutNotice()).willReturn(0);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents1Equals1() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(singletonList(1));
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents2Equals1() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(asList(1, 1));
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfKnownChildSensitiveDataConsents2Equals2() {
        // given
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(asList(1, 2));
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldDisallowIfPersonalDataConsentsEquals2() {
        // given
        given(gppReader.getPersonalDataConsents()).willReturn(2);
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldAllow() {
        // given
        final PrivacyModule target = new USNatSyncUser(gppReader);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ALLOW);
    }
}
