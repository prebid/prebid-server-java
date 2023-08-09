package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UspCaV1;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

public class USCaliforniaGppReaderTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppModel gppModel;

    @Mock
    private UspCaV1 uspCaV1;

    private USCaliforniaGppReader gppReader;

    @Before
    public void setUp() {
        given(gppModel.getUspCaV1Section()).willReturn(uspCaV1);

        gppReader = new USCaliforniaGppReader(gppModel);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getGpc()).willReturn(true);

        // when and then
        assertThat(gppReader.getGpc()).isTrue();
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingNotice()).isNull();
        verifyNoInteractions(uspCaV1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSharingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOut()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSharingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isNull();
        verifyNoInteractions(uspCaV1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isNull();
        verifyNoInteractions(uspCaV1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSensitiveDataLimitUseNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();
        verifyNoInteractions(uspCaV1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getSensitiveDataProcessing()).willReturn(asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing())
                .containsExactly(3, 3, 7, 8, null, 5, 6, 2, 0, 1, null, 4);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnNonChildResult() {
        // given
        given(uspCaV1.getKnownChildSensitiveDataConsents()).willReturn(asList(0, 0));

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(0, 0);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResult() {
        // given
        given(uspCaV1.getKnownChildSensitiveDataConsents()).willReturn(asList(0, 2));

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // given
        given(uspCaV1.getPersonalDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USCaliforniaGppReader(null);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isNull();
        assertThat(gppReader.getGpc()).isNull();
        assertThat(gppReader.getSaleOptOut()).isNull();
        assertThat(gppReader.getSaleOptOutNotice()).isNull();
        assertThat(gppReader.getSharingNotice()).isNull();
        assertThat(gppReader.getSharingOptOut()).isNull();
        assertThat(gppReader.getSharingOptOutNotice()).isNull();
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isNull();
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isNull();
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();
        assertThat(gppReader.getSensitiveDataProcessing()).isEqualTo(Collections.nCopies(12, null));
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isEqualTo(asList(1, 1));
        assertThat(gppReader.getPersonalDataConsents()).isNull();
    }
}
