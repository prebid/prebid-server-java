package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

public interface USCustomLogicGppReader {

    Object getVersion();

    Object getGpc();

    Object getGpcSegmentType();

    Object getGpcSegmentIncluded();

    Object getSaleOptOut();

    Object getSaleOptOutNotice();

    Object getSharingNotice();

    Object getSharingOptOut();

    Object getSharingOptOutNotice();

    Object getTargetedAdvertisingOptOut();

    Object getTargetedAdvertisingOptOutNotice();

    Object getSensitiveDataLimitUseNotice();

    Object getSensitiveDataProcessing();

    Object getSensitiveDataProcessingOptOutNotice();

    Object getKnownChildSensitiveDataConsents();

    Object getPersonalDataConsents();

    Object getMspaCoveredTransaction();

    Object getMspaServiceProviderMode();

    Object getMspaOptOutOptionMode();
}
