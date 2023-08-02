package org.prebid.server.activity.infrastructure.privacy.usnat;

import java.util.List;

public interface USNatGppReader {

    Integer getMspaServiceProviderMode();

    Boolean getGpc();

    Integer getSaleOptOut();

    Integer getSaleOptOutNotice();

    Integer getSharingNotice();

    Integer getSharingOptOut();

    Integer getSharingOptOutNotice();

    Integer getTargetedAdvertisingOptOut();

    Integer getTargetedAdvertisingOptOutNotice();

    Integer getSensitiveDataLimitUseNotice();

    Integer getSensitiveDataProcessingOptOutNotice();

    List<Integer> getSensitiveDataProcessing();

    List<Integer> getKnownChildSensitiveDataConsents();

    Integer getPersonalDataConsents();
}
