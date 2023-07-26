package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;

import java.util.List;
import java.util.Objects;

public class USNatTransmitGeo implements PrivacyModule {

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
                    && equalsAtIndex(2, sensitiveDataProcessing, 7))
                || equalsAtIndex(1, sensitiveDataProcessing, 7);
    }

    private static boolean checkKnownChildSensitiveDataConsents(USNatGppReader gppReader) {
        final List<Integer> knownChildSensitiveDataConsents = gppReader.getKnownChildSensitiveDataConsents();

        return equalsAtIndex(1, knownChildSensitiveDataConsents, 0)
                || equalsAtIndex(1, knownChildSensitiveDataConsents, 1)
                || equalsAtIndex(2, knownChildSensitiveDataConsents, 1);
    }

    private static <T> boolean equalsAtIndex(T expectedValue, List<T> list, int index) {
        return list != null && list.size() > index && equals(list.get(index), expectedValue);
    }

    private static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
