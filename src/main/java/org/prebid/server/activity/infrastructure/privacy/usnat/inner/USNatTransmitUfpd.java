package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class USNatTransmitUfpd implements PrivacyModule {

    private static final Set<Integer> SENSITIVE_DATA_INDICES_SET_1 = Set.of(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11);
    private static final Set<Integer> SENSITIVE_DATA_INDICES_SET_2 = Set.of(0, 1, 2, 3, 4, 10);
    private static final Set<Integer> SENSITIVE_DATA_INDICES_SET_3 = Set.of(5, 6, 8, 9, 11);

    private final Result result;

    public USNatTransmitUfpd(USNatGppReader gppReader) {
        result = disallow(gppReader) ? Result.DISALLOW : Result.ALLOW;
    }

    @Override
    public Result proceed(ActivityCallPayload activityCallPayload) {
        return result;
    }

    public static boolean disallow(USNatGppReader gppReader) {
        return equals(gppReader.getMspaServiceProviderMode(), 1)
                || equals(gppReader.getGpc(), true)
                || checkSale(gppReader)
                || checkSharing(gppReader)
                || checkTargetedAdvertising(gppReader)
                || checkSensitiveData(gppReader)
                || checkKnownChildSensitiveDataConsents(gppReader)
                || equals(gppReader.getPersonalDataConsents(), 2);
    }

    private static boolean checkSale(USNatGppReader gppReader) {
        final Integer saleOptOut = gppReader.getSaleOptOut();
        final Integer saleOptOutNotice = gppReader.getSaleOptOutNotice();

        return equals(saleOptOut, 1)
                || equals(saleOptOutNotice, 2)
                || (equals(saleOptOutNotice, 0) && equals(saleOptOut, 2));
    }

    private static boolean checkSharing(USNatGppReader gppReader) {
        final Integer sharingNotice = gppReader.getSharingNotice();
        final Integer sharingOptOut = gppReader.getSharingOptOut();
        final Integer sharingOptOutNotice = gppReader.getSharingOptOutNotice();

        return equals(sharingNotice, 2)
                || equals(sharingOptOut, 1)
                || equals(sharingOptOutNotice, 2)
                || (equals(sharingNotice, 0) && equals(sharingOptOut, 2))
                || (equals(sharingOptOutNotice, 0) && equals(sharingOptOut, 2));
    }

    private static boolean checkTargetedAdvertising(USNatGppReader gppReader) {
        final Integer targetedAdvertisingOptOut = gppReader.getTargetedAdvertisingOptOut();
        final Integer targetedAdvertisingOptOutNotice = gppReader.getTargetedAdvertisingOptOutNotice();

        return equals(targetedAdvertisingOptOut, 1)
                || equals(targetedAdvertisingOptOutNotice, 2)
                || (equals(targetedAdvertisingOptOutNotice, 0) && equals(targetedAdvertisingOptOut, 2));
    }

    private static boolean checkSensitiveData(USNatGppReader gppReader) {
        final Integer sensitiveDataProcessingOptOutNotice = gppReader.getSensitiveDataProcessingOptOutNotice();
        final Integer sensitiveDataLimitUseNotice = gppReader.getSensitiveDataLimitUseNotice();
        final List<Integer> sensitiveDataProcessing = gppReader.getSensitiveDataProcessing();

        return equals(sensitiveDataProcessingOptOutNotice, 2)
                || equals(sensitiveDataLimitUseNotice, 2)
                || ((equals(sensitiveDataProcessingOptOutNotice, 0) || equals(sensitiveDataLimitUseNotice, 0))
                    && anyEqualsAtIndices(2, sensitiveDataProcessing, SENSITIVE_DATA_INDICES_SET_1))
                || anyEqualsAtIndices(1, sensitiveDataProcessing, SENSITIVE_DATA_INDICES_SET_2)
                || anyEqualsAtIndices(1, sensitiveDataProcessing, SENSITIVE_DATA_INDICES_SET_3)
                || anyEqualsAtIndices(2, sensitiveDataProcessing, SENSITIVE_DATA_INDICES_SET_3);
    }

    private static boolean checkKnownChildSensitiveDataConsents(USNatGppReader gppReader) {
        final List<Integer> knownChildSensitiveDataConsents = gppReader.getKnownChildSensitiveDataConsents();

        return equalsAtIndex(1, knownChildSensitiveDataConsents, 0)
                || equalsAtIndex(1, knownChildSensitiveDataConsents, 1)
                || equalsAtIndex(2, knownChildSensitiveDataConsents, 1);
    }

    private static <T> boolean anyEqualsAtIndices(T expectedValue, List<T> list, Set<Integer> indices) {
        return indices.stream().anyMatch(index -> equalsAtIndex(expectedValue, list, index));
    }

    private static <T> boolean equalsAtIndex(T expectedValue, List<T> list, int index) {
        return list != null && list.size() > index && equals(list.get(index), expectedValue);
    }

    private static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
