package org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsVa;
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
public class USVirginiaGppReaderTest {

    @Mock
    private GppModel gppModel;

    @Mock
    private UsVa usVa;

    private USVirginiaGppReader gppReader;

    @BeforeEach
    public void setUp() {
        given(gppModel.getUsVaSection()).willReturn(usVa);

        gppReader = new USVirginiaGppReader(gppModel);
    }

    @Test
    public void getVersionShouldReturnExpectedResult() {
        // given
        given(usVa.getVersion()).willReturn(1);

        // when and then
        assertThat(gppReader.getVersion()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpc()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getGpcSegmentTypeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentType()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getGpcSegmentIncludedShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentIncluded()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(usVa.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usVa.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // given
        given(usVa.getSharingNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOut()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // given
        given(usVa.getTargetedAdvertisingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usVa.getTargetedAdvertisingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        final List<Integer> data = Collections.emptyList();
        given(usVa.getSensitiveDataProcessing()).willReturn(data);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing()).isSameAs(data);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnExpectedResult() {
        // given
        given(usVa.getKnownChildSensitiveDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isEqualTo(1);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isNull();
        verifyNoInteractions(usVa);
    }

    @Test
    public void getMspaCoveredTransactionShouldReturnExpectedResult() {
        // given
        given(usVa.getMspaCoveredTransaction()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaCoveredTransaction()).isEqualTo(1);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(usVa.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getMspaOptOutOptionModeShouldReturnExpectedResult() {
        // given
        given(usVa.getMspaOptOutOptionMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaOptOutOptionMode()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USVirginiaGppReader(null);

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
