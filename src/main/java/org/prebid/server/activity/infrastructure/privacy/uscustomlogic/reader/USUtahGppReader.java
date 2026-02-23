package org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsUt;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public class USUtahGppReader implements USCustomLogicGppReader {

    private final UsUt consent;

    public USUtahGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsUtSection() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getVersion);
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
        return ObjectUtil.getIfNotNull(consent, UsUt::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getSharingNotice);
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
        return ObjectUtil.getIfNotNull(consent, UsUt::getTargetedAdvertisingOptOut);
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getTargetedAdvertisingOptOutNotice);
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return null;
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getSensitiveDataProcessingOptOutNotice);
    }

    @Override
    public Integer getKnownChildSensitiveDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getKnownChildSensitiveDataConsents);
    }

    @Override
    public Integer getPersonalDataConsents() {
        return null;
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsUt::getMspaOptOutOptionMode);
    }
}
