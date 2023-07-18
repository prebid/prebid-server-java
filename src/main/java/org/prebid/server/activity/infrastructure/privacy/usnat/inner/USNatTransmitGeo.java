package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class USNatTransmitGeo implements PrivacyModule {

    private static final Set<Integer> SENSITIVE_DATA_INDICES_SET_1 = Set.of(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11);

    private final Result result;

    public USNatTransmitGeo(USNatGppReader gppReader) {
        result = disallow(gppReader) ? Result.DISALLOW : Result.ALLOW;
    }

    @Override
    public Result proceed(ActivityCallPayload activityCallPayload) {
        return result;
    }

    public static boolean disallow(USNatGppReader gppReader) {
        return equals(gppReader.getMspaServiceProviderMode(), 1)
                || equals(gppReader.getGpc(), true)
                || checkSensitiveData(gppReader)
                || checkKnownChildSensitiveDataConsents(gppReader)
                || equals(gppReader.getPersonalDataConsents(), 2);
    }

    private static boolean checkSensitiveData(USNatGppReader gppReader) {
        final Integer sensitiveDataProcessingOptOutNotice = gppReader.getSensitiveDataProcessingOptOutNotice();
        final Integer sensitiveDataLimitUseNotice = gppReader.getSensitiveDataLimitUseNotice();
        final List<Integer> sensitiveDataProcessing = gppReader.getSensitiveDataProcessing();

        return equals(sensitiveDataProcessingOptOutNotice, 2)
                || equals(sensitiveDataLimitUseNotice, 2)
                || ((equals(sensitiveDataProcessingOptOutNotice, 0) || equals(sensitiveDataLimitUseNotice, 0))
                    && anyEqualsAtIndices(2, sensitiveDataProcessing, SENSITIVE_DATA_INDICES_SET_1))
                || equalsAtIndex(1, sensitiveDataProcessing, 8);
    }

    private static boolean checkKnownChildSensitiveDataConsents(USNatGppReader gppReader) {
        final List<Integer> knownChildSensitiveDataConsents = gppReader.getKnownChildSensitiveDataConsents();

        return notEqualsAtIndex(0, knownChildSensitiveDataConsents, 1)
                || equalsAtIndex(1, knownChildSensitiveDataConsents, 0);
    }

    private static <T> boolean anyEqualsAtIndices(T expectedValue, List<T> list, Set<Integer> indices) {
        return indices.stream().anyMatch(index -> equalsAtIndex(expectedValue, list, index));
    }

    private static <T> boolean equalsAtIndex(T expectedValue, List<T> list, int index) {
        return list != null && list.size() > index && equals(list.get(index), expectedValue);
    }

    private static <T> boolean notEqualsAtIndex(T notExpectedValue, List<T> list, int index) {
        return list != null && list.size() > index && !equals(list.get(index), notExpectedValue);
    }

    private static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
