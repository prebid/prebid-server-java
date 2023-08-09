package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.Gpc;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.KnownChildSensitiveDataConsent;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.MspaServiceProviderMode;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.PersonalDataConsents;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataLimitUseNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataProcessing;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.SensitiveDataProcessingOptOutNotice;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.model.USNatField;

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
        return equals(gppReader.getMspaServiceProviderMode(), MspaServiceProviderMode.YES)
                || equals(gppReader.getGpc(), Gpc.TRUE)
                || checkSensitiveData(gppReader)
                || checkKnownChildSensitiveDataConsents(gppReader)
                || equals(gppReader.getPersonalDataConsents(), PersonalDataConsents.CONSENT);
    }

    private static boolean checkSensitiveData(USNatGppReader gppReader) {
        final Integer sensitiveDataProcessingOptOutNotice = gppReader.getSensitiveDataProcessingOptOutNotice();
        final Integer sensitiveDataLimitUseNotice = gppReader.getSensitiveDataLimitUseNotice();
        final List<Integer> sensitiveDataProcessing = gppReader.getSensitiveDataProcessing();

        return equals(sensitiveDataProcessingOptOutNotice, SensitiveDataProcessingOptOutNotice.NO)
                || equals(sensitiveDataLimitUseNotice, SensitiveDataLimitUseNotice.NO)
                || ((equals(sensitiveDataProcessingOptOutNotice, SensitiveDataProcessingOptOutNotice.NOT_APPLICABLE)
                        || equals(sensitiveDataLimitUseNotice, SensitiveDataLimitUseNotice.NOT_APPLICABLE))
                    && equalsAtIndex(SensitiveDataProcessing.CONSENT, sensitiveDataProcessing, 7))
                || equalsAtIndex(SensitiveDataProcessing.NO_CONSENT, sensitiveDataProcessing, 7);
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
