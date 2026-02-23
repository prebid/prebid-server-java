package org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsCt;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public class USConnecticutGppReader implements USCustomLogicGppReader {

    private final UsCt consent;

    public USConnecticutGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsCtSection() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getGpc);
    }

    @Override
    public Boolean getGpcSegmentType() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getGpcSegmentIncluded);
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getSharingNotice);
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
        return ObjectUtil.getIfNotNull(consent, UsCt::getTargetedAdvertisingOptOut);
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getTargetedAdvertisingOptOutNotice);
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return null;
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return null;
    }

    @Override
    public List<Integer> getKnownChildSensitiveDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getKnownChildSensitiveDataConsents);
    }

    @Override
    public Integer getPersonalDataConsents() {
        return null;
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsCt::getMspaOptOutOptionMode);
    }
}
