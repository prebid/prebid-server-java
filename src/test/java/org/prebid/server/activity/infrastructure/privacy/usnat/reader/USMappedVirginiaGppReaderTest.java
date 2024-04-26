package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UspVaV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class USMappedVirginiaGppReaderTest {

    @Mock
    private GppModel gppModel;

    @Mock
    private UspVaV1 uspVaV1;

    private USMappedVirginiaGppReader gppReader;

    @BeforeEach
    public void setUp() {
        given(gppModel.getUspVaV1Section()).willReturn(uspVaV1);

        gppReader = new USMappedVirginiaGppReader(gppModel);
    }

    @Test
    public void getVersionShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getVersion()).willReturn(1);

        // when and then
        assertThat(gppReader.getVersion()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpc()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getGpcSegmentTypeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentType()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getGpcSegmentIncludedShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentIncluded()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getSharingNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOut()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getTargetedAdvertisingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getTargetedAdvertisingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        final List<Integer> data = Collections.emptyList();
        given(uspVaV1.getSensitiveDataProcessing()).willReturn(data);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing()).isSameAs(data);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn1() {
        // given
        given(uspVaV1.getKnownChildSensitiveDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn2() {
        // given
        given(uspVaV1.getKnownChildSensitiveDataConsents()).willReturn(2);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnNonChildResultOn0() {
        // given
        given(uspVaV1.getKnownChildSensitiveDataConsents()).willReturn(0);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(0, 0);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isNull();
        verifyNoInteractions(uspVaV1);
    }

    @Test
    public void getMspaCoveredTransactionShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getMspaCoveredTransaction()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaCoveredTransaction()).isEqualTo(1);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getMspaOptOutOptionModeShouldReturnExpectedResult() {
        // given
        given(uspVaV1.getMspaOptOutOptionMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaOptOutOptionMode()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USMappedVirginiaGppReader(null);

        // when and then
        assertThat(gppReader.getVersion()).isNull();

        assertThat(gppReader.getGpc()).isNull();
        assertThat(gppReader.getGpcSegmentType()).isNull();
        assertThat(gppReader.getGpcSegmentIncluded()).isNull();

        assertThat(gppReader.getSaleOptOut()).isNull();
        assertThat(gppReader.getSaleOptOutNotice()).isNull();

        assertThat(gppReader.getSharingNotice()).isNull();
        assertThat(gppReader.getSharingOptOut()).isNull();
        assertThat(gppReader.getSharingOptOutNotice()).isNull();

        assertThat(gppReader.getTargetedAdvertisingOptOut()).isNull();
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isNull();

        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        assertThat(gppReader.getSensitiveDataProcessing()).isNull();
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();

        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isNull();
        assertThat(gppReader.getPersonalDataConsents()).isNull();

        assertThat(gppReader.getMspaCoveredTransaction()).isNull();
        assertThat(gppReader.getMspaServiceProviderMode()).isNull();
        assertThat(gppReader.getMspaOptOutOptionMode()).isNull();
    }
}
