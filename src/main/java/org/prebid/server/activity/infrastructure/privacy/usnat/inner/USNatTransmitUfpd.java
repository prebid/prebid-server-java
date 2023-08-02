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
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataLimitUseNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataProcessing;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataProcessingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingOptOut;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SharingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.TargetedAdvertisingOptOut;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.TargetedAdvertisingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.USNatField;

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
        return equals(gppReader.getMspaServiceProviderMode(), MspaServiceProviderMode.YES)
                || equals(gppReader.getGpc(), Gpc.TRUE)
                || checkSale(gppReader)
                || checkSharing(gppReader)
                || checkTargetedAdvertising(gppReader)
                || checkSensitiveData(gppReader)
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

    private static boolean checkSensitiveData(USNatGppReader gppReader) {
        final Integer sensitiveDataProcessingOptOutNotice = gppReader.getSensitiveDataProcessingOptOutNotice();
        final Integer sensitiveDataLimitUseNotice = gppReader.getSensitiveDataLimitUseNotice();
        final List<Integer> sensitiveDataProcessing = gppReader.getSensitiveDataProcessing();

        return equals(sensitiveDataProcessingOptOutNotice, SensitiveDataProcessingOptOutNotice.NO)
                || equals(sensitiveDataLimitUseNotice, SensitiveDataLimitUseNotice.NO)
                || ((equals(sensitiveDataProcessingOptOutNotice, SensitiveDataProcessingOptOutNotice.NOT_APPLICABLE)
                        || equals(sensitiveDataLimitUseNotice, SensitiveDataLimitUseNotice.NOT_APPLICABLE))
                    && anyEqualsAtIndices(
                        SensitiveDataProcessing.CONSENT,
                        sensitiveDataProcessing,
                        SENSITIVE_DATA_INDICES_SET_1))
                || anyEqualsAtIndices(
                    SensitiveDataProcessing.NO_CONSENT,
                    sensitiveDataProcessing,
                    SENSITIVE_DATA_INDICES_SET_2)
                || anyEqualsAtIndices(
                    SensitiveDataProcessing.NO_CONSENT,
                    sensitiveDataProcessing,
                    SENSITIVE_DATA_INDICES_SET_3)
                || anyEqualsAtIndices(
                    SensitiveDataProcessing.CONSENT,
                    sensitiveDataProcessing,
                    SENSITIVE_DATA_INDICES_SET_3);
    }

    private static boolean checkKnownChildSensitiveDataConsents(USNatGppReader gppReader) {
        final List<Integer> knownChildSensitiveDataConsents = gppReader.getKnownChildSensitiveDataConsents();

        return equalsAtIndex(KnownChildSensitiveDataConsent.NO_CONSENT, knownChildSensitiveDataConsents, 0)
                || equalsAtIndex(KnownChildSensitiveDataConsent.NO_CONSENT, knownChildSensitiveDataConsents, 1)
                || equalsAtIndex(KnownChildSensitiveDataConsent.CONSENT, knownChildSensitiveDataConsents, 1);
    }

    private static <T> boolean anyEqualsAtIndices(USNatField<T> expectedValue, List<T> list, Set<Integer> indices) {
        return indices.stream().anyMatch(index -> equalsAtIndex(expectedValue, list, index));
    }

    private static <T> boolean equalsAtIndex(USNatField<T> expectedValue, List<T> list, int index) {
        return list != null && list.size() > index && equals(list.get(index), expectedValue);
    }

    private static <T> boolean equals(T providedValue, USNatField<T> expectedValue) {
        return Objects.equals(providedValue, expectedValue.value());
    }
}
