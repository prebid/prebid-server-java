package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UspUtV1;
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

public class USUtahGppReaderTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppModel gppModel;

    @Mock
    private UspUtV1 uspUtV1;

    private USUtahGppReader gppReader;

    @Before
    public void setUp() {
        given(gppModel.getUspUtV1Section()).willReturn(uspUtV1);

        gppReader = new USUtahGppReader(gppModel);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpc()).isNull();
        verifyNoInteractions(uspUtV1);
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getSharingNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOut()).isNull();
        verifyNoInteractions(uspUtV1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isNull();
        verifyNoInteractions(uspUtV1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getTargetedAdvertisingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getTargetedAdvertisingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        verifyNoInteractions(uspUtV1);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getSensitiveDataProcessingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        given(uspUtV1.getSensitiveDataProcessing()).willReturn(asList(0, 1, 2, 3, 4, 5, 6, 7));

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing())
                .containsExactly(0, 1, 4, 2, 3, 5, 6, 7);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn1() {
        // given
        given(uspUtV1.getKnownChildSensitiveDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn2() {
        // given
        given(uspUtV1.getKnownChildSensitiveDataConsents()).willReturn(2);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnNonChildResultOn0() {
        // given
        given(uspUtV1.getKnownChildSensitiveDataConsents()).willReturn(0);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(0, 0);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isNull();
        verifyNoInteractions(uspUtV1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USUtahGppReader(null);

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
        assertThat(gppReader.getSensitiveDataProcessing()).isEqualTo(Collections.nCopies(8, null));
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isNull();
        assertThat(gppReader.getPersonalDataConsents()).isNull();
    }
}
