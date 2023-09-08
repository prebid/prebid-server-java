package org.prebid.server.activity.infrastructure.privacy.usnat;

import java.util.List;

public interface USNatGppReader {

    Integer getVersion();

    Boolean getGpc();

    Boolean getGpcSegmentType();

    Boolean getGpcSegmentIncluded();

    Integer getSaleOptOut();

    Integer getSaleOptOutNotice();

    Integer getSharingNotice();

    Integer getSharingOptOut();

    Integer getSharingOptOutNotice();

    Integer getTargetedAdvertisingOptOut();

    Integer getTargetedAdvertisingOptOutNotice();

    Integer getSensitiveDataLimitUseNotice();

    List<Integer> getSensitiveDataProcessing();

    Integer getSensitiveDataProcessingOptOutNotice();

    List<Integer> getKnownChildSensitiveDataConsents();

    Integer getPersonalDataConsents();

    Integer getMspaCoveredTransaction();

    Integer getMspaServiceProviderMode();

    Integer getMspaOptOutOptionMode();
}
