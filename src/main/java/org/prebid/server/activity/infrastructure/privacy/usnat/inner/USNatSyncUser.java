package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.Gpc;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.KnownChildSensitiveDataConsent;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.MspaServiceProviderMode;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.PersonalDataConsents;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SaleOptOut;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SaleOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingOptOut;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.TargetedAdvertisingOptOut;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.TargetedAdvertisingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.USNatField;

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
        return equals(gppReader.getMspaServiceProviderMode(), MspaServiceProviderMode.YES)
                || equals(gppReader.getGpc(), Gpc.TRUE)
                || checkSale(gppReader)
                || checkSharing(gppReader)
                || checkTargetedAdvertising(gppReader)
                || checkKnownChildSensitiveDataConsents(gppReader)
                || equals(gppReader.getPersonalDataConsents(), PersonalDataConsents.CONSENT);
    }

    private static boolean checkSale(USNatGppReader gppReader) {
        final Integer saleOptOut = gppReader.getSaleOptOut();
        final Integer saleOptOutNotice = gppReader.getSaleOptOutNotice();

        return equals(saleOptOut, SaleOptOut.OPTED_OUT)
                || equals(saleOptOutNotice, SaleOptOutNotice.NO)
                || (equals(saleOptOutNotice, SaleOptOutNotice.NOT_APPLICABLE)
                    && equals(saleOptOut, SaleOptOut.DID_NOT_OPT_OUT));
    }

    private static boolean checkSharing(USNatGppReader gppReader) {
        final Integer sharingNotice = gppReader.getSharingNotice();
        final Integer sharingOptOut = gppReader.getSharingOptOut();
        final Integer sharingOptOutNotice = gppReader.getSharingOptOutNotice();

        return equals(sharingNotice, SharingNotice.NO)
                || equals(sharingOptOut, SharingOptOut.OPTED_OUT)
                || equals(sharingOptOutNotice, SharingOptOutNotice.NO)
                || (equals(sharingNotice, SharingNotice.NOT_APPLICABLE)
                    && equals(sharingOptOut, SharingOptOut.DID_NOT_OPT_OUT))
                || (equals(sharingOptOutNotice, SharingOptOutNotice.NOT_APPLICABLE)
                    && equals(sharingOptOut, SharingOptOut.DID_NOT_OPT_OUT));
    }

    private static boolean checkTargetedAdvertising(USNatGppReader gppReader) {
        final Integer targetedAdvertisingOptOut = gppReader.getTargetedAdvertisingOptOut();
        final Integer targetedAdvertisingOptOutNotice = gppReader.getTargetedAdvertisingOptOutNotice();

        return equals(targetedAdvertisingOptOut, TargetedAdvertisingOptOut.OPTED_OUT)
                || equals(targetedAdvertisingOptOutNotice, TargetedAdvertisingOptOutNotice.NO)
                || (equals(targetedAdvertisingOptOutNotice, TargetedAdvertisingOptOutNotice.NOT_APPLICABLE)
                    && equals(targetedAdvertisingOptOut, TargetedAdvertisingOptOut.DID_NOT_OPT_OUT));
    }

    private static boolean checkKnownChildSensitiveDataConsents(USNatGppReader gppReader) {
        final List<Integer> knownChildSensitiveDataConsents = gppReader.getKnownChildSensitiveDataConsents();

        return equalsAtIndex(KnownChildSensitiveDataConsent.NO_CONSENT, knownChildSensitiveDataConsents, 0)
                || equalsAtIndex(KnownChildSensitiveDataConsent.NO_CONSENT, knownChildSensitiveDataConsents, 1)
                || equalsAtIndex(KnownChildSensitiveDataConsent.CONSENT, knownChildSensitiveDataConsents, 1);
    }

    private static <T> boolean equalsAtIndex(USNatField<T> expectedValue, List<T> list, int index) {
        return list != null && list.size() > index && equals(list.get(index), expectedValue);
    }

    private static <T> boolean equals(T providedValue, USNatField<T> expectedValue) {
        return Objects.equals(providedValue, expectedValue.value());
    }
}
