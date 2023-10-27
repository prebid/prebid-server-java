package org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UspUtV1;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public class USUtahGppReader implements USCustomLogicGppReader {

    private final UspUtV1 consent;

    public USUtahGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUspUtV1Section() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentType() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return null;
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getSharingNotice);
    }

    @Override
    public Integer getSharingOptOut() {
        return null;
    }

    @Override
    public Integer getSharingOptOutNotice() {
        return null;
    }

    @Override
    public Integer getTargetedAdvertisingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getTargetedAdvertisingOptOut);
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getTargetedAdvertisingOptOutNotice);
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return null;
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getSensitiveDataProcessingOptOutNotice);
    }

    @Override
    public Integer getKnownChildSensitiveDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getKnownChildSensitiveDataConsents);
    }

    @Override
    public Integer getPersonalDataConsents() {
        return null;
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UspUtV1::getMspaOptOutOptionMode);
    }
}
