package org.prebid.server.activity.infrastructure.privacy.usnat.reader;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UsNat;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public class USNationalGppReader implements USNatGppReader, USCustomLogicGppReader {

    private final UsNat consent;

    public USNationalGppReader(GppModel gppModel) {
        consent = gppModel != null ? gppModel.getUsNatSection() : null;
    }

    @Override
    public Integer getVersion() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getVersion);
    }

    @Override
    public Boolean getGpc() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getGpc);
    }

    @Override
    public Boolean getGpcSegmentType() {
        /*
        TODO: return the GpcSegmentType field when the issue is resolved
         https://github.com/IABTechLab/iabgpp-java/issues/28
         */
        return null;
    }

    @Override
    public Boolean getGpcSegmentIncluded() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getGpcSegmentIncluded);
    }

    @Override
    public Integer getSaleOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSaleOptOut);
    }

    @Override
    public Integer getSaleOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSaleOptOutNotice);
    }

    @Override
    public Integer getSharingNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSharingNotice);
    }

    @Override
    public Integer getSharingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSharingOptOut);
    }

    @Override
    public Integer getSharingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSharingOptOutNotice);
    }

    @Override
    public Integer getTargetedAdvertisingOptOut() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getTargetedAdvertisingOptOut);
    }

    @Override
    public Integer getTargetedAdvertisingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getTargetedAdvertisingOptOutNotice);
    }

    @Override
    public Integer getSensitiveDataLimitUseNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSensitiveDataLimitUseNotice);
    }

    @Override
    public List<Integer> getSensitiveDataProcessing() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSensitiveDataProcessing);
    }

    @Override
    public Integer getSensitiveDataProcessingOptOutNotice() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getSensitiveDataProcessingOptOutNotice);
    }

    @Override
    public List<Integer> getKnownChildSensitiveDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getKnownChildSensitiveDataConsents);
    }

    @Override
    public Integer getPersonalDataConsents() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getPersonalDataConsents);
    }

    @Override
    public Integer getMspaCoveredTransaction() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getMspaCoveredTransaction);
    }

    @Override
    public Integer getMspaServiceProviderMode() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getMspaServiceProviderMode);
    }

    @Override
    public Integer getMspaOptOutOptionMode() {
        return ObjectUtil.getIfNotNull(consent, UsNat::getMspaOptOutOptionMode);
    }
}
