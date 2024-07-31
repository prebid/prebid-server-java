package org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsCaV1;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public class USCaliforniaGppReader implements USCustomLogicGppReader {

    private final UsCaV1 consent;

    public USCaliforniaGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsCaV1Section() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getGpc);
    }

    @Override
    public Boolean getGpcSegmentType() {
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getGpcSegmentIncluded);
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return null;
    }

    @Override
    public Integer getSharingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSharingOptOut);
    }

    @Override
    public Integer getSharingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSharingOptOutNotice);
    }

    @Override
    public Integer getTargetedAdvertisingOptOut() {
        return null;
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return null;
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSensitiveDataLimitUseNotice);
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return null;
    }

    @Override
    public List<Integer> getKnownChildSensitiveDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getKnownChildSensitiveDataConsents);
    }

    @Override
    public Integer getPersonalDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getPersonalDataConsents);
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsCaV1::getMspaOptOutOptionMode);
    }
}
