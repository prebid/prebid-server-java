package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class USCustomLogicDataSupplierTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USCustomLogicGppReader gppReader;

    private USCustomLogicDataSupplier target;

    @BeforeEach
    public void setUp() {
        target = USCustomLogicDataSupplier.of(gppReader);
    }

    @Test
    public void getShouldReturnExpectedResult() {
        // given
        given(gppReader.getVersion()).willReturn(0);

        given(gppReader.getGpc()).willReturn(1);
        given(gppReader.getGpcSegmentType()).willReturn(2);
        given(gppReader.getGpcSegmentIncluded()).willReturn(3);

        given(gppReader.getSaleOptOut()).willReturn(4);
        given(gppReader.getSaleOptOutNotice()).willReturn(5);

        given(gppReader.getSharingNotice()).willReturn(6);
        given(gppReader.getSharingOptOut()).willReturn(7);
        given(gppReader.getSharingOptOutNotice()).willReturn(8);

        given(gppReader.getTargetedAdvertisingOptOut()).willReturn(9);
        given(gppReader.getTargetedAdvertisingOptOutNotice()).willReturn(10);

        given(gppReader.getSensitiveDataLimitUseNotice()).willReturn(11);
        given(gppReader.getSensitiveDataProcessing()).willReturn(12);
        given(gppReader.getSensitiveDataProcessingOptOutNotice()).willReturn(13);

        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(14);

        given(gppReader.getPersonalDataConsents()).willReturn(15);

        given(gppReader.getMspaCoveredTransaction()).willReturn(16);
        given(gppReader.getMspaServiceProviderMode()).willReturn(17);
        given(gppReader.getMspaOptOutOptionMode()).willReturn(18);

        // when
        final Map<String, Object> result = target.get();

        // then
        assertThat(result).containsExactlyEntriesOf(expectedData());
    }

    @Test
    public void getShouldCorrectlyHandleLists() {
        // given
        given(gppReader.getVersion()).willReturn(0);
        given(gppReader.getKnownChildSensitiveDataConsents()).willReturn(asList(9, 8, 7, 6, 5, 4, 3, 2, 1));

        // when
        final Map<String, Object> result = target.get();

        // then
        assertThat(result).containsAllEntriesOf(Map.of(
                "Version", 0,
                "KnownChildSensitiveDataConsents1", 9,
                "KnownChildSensitiveDataConsents2", 8,
                "KnownChildSensitiveDataConsents3", 7,
                "KnownChildSensitiveDataConsents4", 6,
                "KnownChildSensitiveDataConsents5", 5,
                "KnownChildSensitiveDataConsents6", 4,
                "KnownChildSensitiveDataConsents7", 3,
                "KnownChildSensitiveDataConsents8", 2,
                "KnownChildSensitiveDataConsents9", 1));
    }

    @NonNull
    private static Map<String, Object> expectedData() {
        final Map<String, Object> data = new HashMap<>();

        data.put("Version", 0);

        data.put("Gpc", 1);
        data.put("GpcSegmentType", 2);
        data.put("GpcSegmentIncluded", 3);

        data.put("SaleOptOut", 4);
        data.put("SaleOptOutNotice", 5);

        data.put("SharingNotice", 6);
        data.put("SharingOptOut", 7);
        data.put("SharingOptOutNotice", 8);

        data.put("TargetedAdvertisingOptOut", 9);
        data.put("TargetedAdvertisingOptOutNotice", 10);

        data.put("SensitiveDataLimitUseNotice", 11);
        data.put("SensitiveDataProcessing", 12);
        data.put("SensitiveDataProcessingOptOutNotice", 13);

        data.put("KnownChildSensitiveDataConsents", 14);

        data.put("PersonalDataConsents", 15);

        data.put("MspaCoveredTransaction", 16);
        data.put("MspaServiceProviderMode", 17);
        data.put("MspaOptOutOptionMode", 18);

        return data;
    }
}
