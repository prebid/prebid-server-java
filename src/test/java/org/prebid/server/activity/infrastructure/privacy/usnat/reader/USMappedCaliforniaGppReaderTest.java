package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsCa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class USMappedCaliforniaGppReaderTest {

    @Mock
    private GppModel gppModel;

    @Mock
    private UsCa usCa;

    private USMappedCaliforniaGppReader gppReader;

    @BeforeEach
    public void setUp() {
        given(gppModel.getUsCaSection()).willReturn(usCa);

        gppReader = new USMappedCaliforniaGppReader(gppModel);
    }

    @Test
    public void getVersionShouldReturnExpectedResult() {
        // given
        given(usCa.getVersion()).willReturn(1);

        // when and then
        assertThat(gppReader.getVersion()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // given
        given(usCa.getGpc()).willReturn(true);

        // when and then
        assertThat(gppReader.getGpc()).isTrue();
    }

    @Test
    public void getGpcSegmentTypeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentType()).isNull();
        verifyNoInteractions(usCa);
    }

    @Test
    public void getGpcSegmentIncludedShouldReturnExpectedResult() {
        // given
        given(usCa.getGpcSegmentIncluded()).willReturn(true);

        // when and then
        assertThat(gppReader.getGpcSegmentIncluded()).isTrue();
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(usCa.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usCa.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingNotice()).isNull();
        verifyNoInteractions(usCa);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // given
        given(usCa.getSharingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOut()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usCa.getSharingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isNull();
        verifyNoInteractions(usCa);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isNull();
        verifyNoInteractions(usCa);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // given
        given(usCa.getSensitiveDataLimitUseNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        given(usCa.getSensitiveDataProcessing()).willReturn(asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing())
                .containsExactly(3, 3, 7, 8, null, 5, 6, 2, 0, 1, null, 4);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();
        verifyNoInteractions(usCa);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnNonChildResult() {
        // given
        given(usCa.getKnownChildSensitiveDataConsents()).willReturn(asList(0, 0));

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(0, 0);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResult() {
        // given
        given(usCa.getKnownChildSensitiveDataConsents()).willReturn(asList(0, 2));

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // given
        given(usCa.getPersonalDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isEqualTo(1);
    }

    @Test
    public void getMspaCoveredTransactionShouldReturnExpectedResult() {
        // given
        given(usCa.getMspaCoveredTransaction()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaCoveredTransaction()).isEqualTo(1);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(usCa.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getMspaOptOutOptionModeShouldReturnExpectedResult() {
        // given
        given(usCa.getMspaOptOutOptionMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaOptOutOptionMode()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USMappedCaliforniaGppReader(null);

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
        assertThat(gppReader.getSensitiveDataProcessing()).isEqualTo(Collections.nCopies(12, null));
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();

        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isEqualTo(asList(1, 1));
        assertThat(gppReader.getPersonalDataConsents()).isNull();

        assertThat(gppReader.getMspaCoveredTransaction()).isNull();
        assertThat(gppReader.getMspaServiceProviderMode()).isNull();
        assertThat(gppReader.getMspaOptOutOptionMode()).isNull();
    }
}
