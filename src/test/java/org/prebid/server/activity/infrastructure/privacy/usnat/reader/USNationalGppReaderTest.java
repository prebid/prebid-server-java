package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsNat;
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
public class USNationalGppReaderTest {

    @Mock
    private GppModel gppModel;

    @Mock
    private UsNat usNat;

    private USNationalGppReader gppReader;

    @BeforeEach
    public void setUp() {
        given(gppModel.getUsNatSection()).willReturn(usNat);

        gppReader = new USNationalGppReader(gppModel);
    }

    @Test
    public void getVersionShouldReturnExpectedResult() {
        // given
        given(usNat.getVersion()).willReturn(1);

        // when and then
        assertThat(gppReader.getVersion()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // given
        given(usNat.getGpc()).willReturn(true);

        // when and then
        assertThat(gppReader.getGpc()).isTrue();
    }

    @Test
    public void getGpcSegmentTypeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentType()).isNull();
        verifyNoInteractions(usNat);
    }

    @Test
    public void getGpcSegmentIncludedShouldReturnExpectedResult() {
        // given
        given(usNat.getGpcSegmentIncluded()).willReturn(true);

        // when and then
        assertThat(gppReader.getGpcSegmentIncluded()).isTrue();
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(usNat.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getSharingNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // given
        given(usNat.getSharingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOut()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getSharingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // given
        given(usNat.getTargetedAdvertisingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getTargetedAdvertisingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getSensitiveDataLimitUseNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        final List<Integer> data = Collections.emptyList();
        given(usNat.getSensitiveDataProcessing()).willReturn(data);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing()).isSameAs(data);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usNat.getSensitiveDataProcessingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnExpectedResult() {
        // given
        final List<Integer> data = Collections.emptyList();
        given(usNat.getKnownChildSensitiveDataConsents()).willReturn(data);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isSameAs(data);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // given
        given(usNat.getPersonalDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isEqualTo(1);
    }

    @Test
    public void getMspaCoveredTransactionShouldReturnExpectedResult() {
        // given
        given(usNat.getMspaCoveredTransaction()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaCoveredTransaction()).isEqualTo(1);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(usNat.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getMspaOptOutOptionModeShouldReturnExpectedResult() {
        // given
        given(usNat.getMspaOptOutOptionMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaOptOutOptionMode()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USNationalGppReader(null);

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
