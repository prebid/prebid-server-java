package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsUtV1;
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
public class USMappedUtahGppReaderTest {

    @Mock
    private GppModel gppModel;

    @Mock
    private UsUtV1 usUtV1;

    private USMappedUtahGppReader gppReader;

    @BeforeEach
    public void setUp() {
        given(gppModel.getUsUtV1Section()).willReturn(usUtV1);

        gppReader = new USMappedUtahGppReader(gppModel);
    }

    @Test
    public void getVersionShouldReturnExpectedResult() {
        // given
        given(usUtV1.getVersion()).willReturn(1);

        // when and then
        assertThat(gppReader.getVersion()).isEqualTo(1);
    }

    @Test
    public void getGpcShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpc()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getGpcSegmentTypeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentType()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getGpcSegmentIncludedShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getGpcSegmentIncluded()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getSaleOptOutShouldReturnExpectedResult() {
        // given
        given(usUtV1.getSaleOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOut()).isEqualTo(1);
    }

    @Test
    public void getSaleOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getSaleOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSaleOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingNoticeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getSharingNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSharingNotice()).isEqualTo(1);
    }

    @Test
    public void getSharingOptOutShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOut()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getSharingOptOutNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSharingOptOutNotice()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getTargetedAdvertisingOptOutShouldReturnExpectedResult() {
        // given
        given(usUtV1.getTargetedAdvertisingOptOut()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOut()).isEqualTo(1);
    }

    @Test
    public void getTargetedAdvertisingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getTargetedAdvertisingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getTargetedAdvertisingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getSensitiveDataLimitUseNoticeShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getSensitiveDataLimitUseNotice()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getSensitiveDataProcessingShouldReturnExpectedResult() {
        // given
        given(usUtV1.getSensitiveDataProcessing()).willReturn(asList(0, 1, 2, 3, 4, 5, 6, 7));

        // when and then
        assertThat(gppReader.getSensitiveDataProcessing())
                .containsExactly(0, 1, 4, 2, 3, 5, 6, 7);
    }

    @Test
    public void getSensitiveDataProcessingOptOutNoticeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getSensitiveDataProcessingOptOutNotice()).willReturn(1);

        // when and then
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isEqualTo(1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn1() {
        // given
        given(usUtV1.getKnownChildSensitiveDataConsents()).willReturn(1);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnChildResultOn2() {
        // given
        given(usUtV1.getKnownChildSensitiveDataConsents()).willReturn(2);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(1, 1);
    }

    @Test
    public void getKnownChildSensitiveDataConsentsShouldReturnNonChildResultOn0() {
        // given
        given(usUtV1.getKnownChildSensitiveDataConsents()).willReturn(0);

        // when and then
        assertThat(gppReader.getKnownChildSensitiveDataConsents()).containsExactly(0, 0);
    }

    @Test
    public void getPersonalDataConsentsShouldReturnExpectedResult() {
        // when and then
        assertThat(gppReader.getPersonalDataConsents()).isNull();
        verifyNoInteractions(usUtV1);
    }

    @Test
    public void getMspaCoveredTransactionShouldReturnExpectedResult() {
        // given
        given(usUtV1.getMspaCoveredTransaction()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaCoveredTransaction()).isEqualTo(1);
    }

    @Test
    public void getMspaServiceProviderModeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getMspaServiceProviderMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaServiceProviderMode()).isEqualTo(1);
    }

    @Test
    public void getMspaOptOutOptionModeShouldReturnExpectedResult() {
        // given
        given(usUtV1.getMspaOptOutOptionMode()).willReturn(1);

        // when and then
        assertThat(gppReader.getMspaOptOutOptionMode()).isEqualTo(1);
    }

    @Test
    public void gppReaderShouldReturnExpectedResultsIfSectionAbsent() {
        // given
        gppReader = new USMappedUtahGppReader(null);

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
        assertThat(gppReader.getSensitiveDataProcessing()).isEqualTo(Collections.nCopies(8, null));
        assertThat(gppReader.getSensitiveDataProcessingOptOutNotice()).isNull();

        assertThat(gppReader.getKnownChildSensitiveDataConsents()).isNull();
        assertThat(gppReader.getPersonalDataConsents()).isNull();

        assertThat(gppReader.getMspaCoveredTransaction()).isNull();
        assertThat(gppReader.getMspaServiceProviderMode()).isNull();
        assertThat(gppReader.getMspaOptOutOptionMode()).isNull();
    }
}
