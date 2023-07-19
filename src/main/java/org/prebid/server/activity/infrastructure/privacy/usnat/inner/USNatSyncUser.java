package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;

import java.util.List;
import java.util.Objects;

public class USNatSyncUser implements PrivacyModule {

    private final Result result;

    public USNatSyncUser(USNatGppReader gppReader) {
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
